'use strict';

const el = (id) => document.getElementById(id);
const ui = {
    process: el('process'),
    start: el('start'),
    error: el('error'),
    messages: el('messages'),
    composer: el('composer'),
    input: el('input'),
    send: el('send'),
    stateEmpty: el('state-empty'),
    state: el('state'),
    processName: el('process-name'),
    status: el('status'),
    stateId: el('state-id'),
    stateObjective: el('state-objective'),
    missing: el('missing'),
    collected: el('collected'),
    turn: el('turn'),
};

let conversationId = null;
let busy = false;

/** Calls the API and turns a ProblemDetail response into an Error with its detail message. */
async function api(method, path, body) {
    const response = await fetch(path, {
        method,
        headers: body ? {'Content-Type': 'application/json'} : undefined,
        body: body ? JSON.stringify(body) : undefined,
    });
    const text = await response.text();
    const payload = text ? JSON.parse(text) : null;
    if (!response.ok) {
        throw new Error(payload?.detail || payload?.message || `${response.status} ${response.statusText}`);
    }
    return payload;
}

function showError(message) {
    ui.error.textContent = message;
    ui.error.hidden = !message;
}

function setBusy(value) {
    busy = value;
    const closed = !conversationId || ui.status.dataset.status === 'COMPLETED';
    ui.input.disabled = busy || closed;
    ui.send.disabled = busy || closed;
    ui.start.disabled = busy;
    if (!busy && !closed) {
        ui.input.focus();
    }
}

function addMessage(role, content, meta) {
    const empty = ui.messages.querySelector('.empty');
    if (empty) {
        empty.remove();
    }
    const node = document.createElement('div');
    node.className = `message ${role}`;
    node.textContent = content;
    if (meta) {
        const label = document.createElement('span');
        label.className = 'meta';
        label.textContent = meta;
        node.appendChild(label);
    }
    ui.messages.appendChild(node);
    ui.messages.scrollTop = ui.messages.scrollHeight;
    return node;
}

function renderState(conversation, turn) {
    ui.stateEmpty.hidden = true;
    ui.state.hidden = false;

    ui.processName.textContent = conversation.processName || conversation.processId;
    ui.status.textContent = conversation.status;
    ui.status.dataset.status = conversation.status;
    ui.status.classList.toggle('completed', conversation.status === 'COMPLETED');
    ui.stateId.textContent = conversation.currentState.id;
    ui.stateObjective.textContent = conversation.currentState.objective;

    ui.missing.replaceChildren(...(conversation.missingFields.length
        ? conversation.missingFields.map((field) => {
            const li = document.createElement('li');
            li.textContent = field;
            return li;
        })
        : [Object.assign(document.createElement('li'), {textContent: 'none'})]));

    const entries = Object.entries(conversation.collectedData || {});
    ui.collected.replaceChildren(...entries.map(([key, value]) => {
        const row = document.createElement('tr');
        const name = document.createElement('td');
        name.textContent = key;
        const cell = document.createElement('td');
        cell.textContent = value === null ? '—' : String(value);
        row.append(name, cell);
        return row;
    }));
    if (!entries.length) {
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 2;
        cell.textContent = 'nothing yet';
        row.appendChild(cell);
        ui.collected.appendChild(row);
    }

    const lines = [];
    if (turn) {
        if (turn.switchedFromProcess) {
            lines.push(`switched process ${turn.switchedFromProcess} → ${conversation.processId}`);
        }
        lines.push(turn.transitioned
            ? `moved ${turn.previousState} → ${conversation.currentState.id}`
            : `stayed in ${conversation.currentState.id}`);
        const accepted = Object.keys(turn.acceptedFields || {});
        if (accepted.length) {
            lines.push(`accepted: ${accepted.join(', ')}`);
        }
        if (turn.ignoredFields?.length) {
            lines.push(`ignored: ${turn.ignoredFields.join(', ')}`);
        }
        if (turn.transitionNote) {
            lines.push(turn.transitionNote);
        }
    }
    if (conversation.choices) {
        const offered = conversation.choices.options.map((option) => option.label).join(', ');
        lines.push(`offering ${conversation.choices.field}: ${offered}`);
    }
    ui.turn.replaceChildren(...(lines.length ? lines : ['—']).map((line) => {
        const li = document.createElement('li');
        li.textContent = line;
        if (turn?.transitionNote && line === turn.transitionNote) {
            li.className = 'note';
        }
        return li;
    }));
}

async function loadProcesses() {
    try {
        const processes = await api('GET', '/api/processes');
        ui.process.replaceChildren(...processes.map((process) => {
            const option = document.createElement('option');
            option.value = process.id;
            option.textContent = process.name || process.id;
            option.title = process.description || '';
            return option;
        }));
        ui.start.disabled = processes.length === 0;
        if (!processes.length) {
            showError('No processes are defined.');
        }
    } catch (e) {
        showError(`Could not load processes: ${e.message}`);
    }
}

async function startConversation() {
    showError('');
    setBusy(true);
    try {
        const result = await api('POST', '/api/conversations', {processId: ui.process.value});
        conversationId = result.conversation.id;
        ui.messages.replaceChildren();
        addMessage('assistant', result.greeting.content, result.greeting.state);
        renderState(result.conversation, null);
    } catch (e) {
        showError(e.message);
    } finally {
        setBusy(false);
    }
}

async function sendMessage(content) {
    showError('');
    addMessage('user', content);
    const pending = addMessage('assistant', 'Thinking…');
    pending.classList.add('pending');
    setBusy(true);
    try {
        const turn = await api('POST', `/api/conversations/${conversationId}/messages`, {content});
        pending.remove();
        addMessage('assistant', turn.reply.content, turn.reply.state);
        renderState(turn.conversation, turn);
    } catch (e) {
        pending.remove();
        showError(e.message);
    } finally {
        setBusy(false);
    }
}

ui.start.addEventListener('click', startConversation);

ui.composer.addEventListener('submit', (event) => {
    event.preventDefault();
    const content = ui.input.value.trim();
    if (!content || busy || !conversationId) {
        return;
    }
    ui.input.value = '';
    sendMessage(content);
});

// Enter sends, Shift+Enter starts a new line.
ui.input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        ui.composer.requestSubmit();
    }
});

loadProcesses();
