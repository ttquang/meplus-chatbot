package com.ttq.engine;

import com.ttq.ChatbotProperties;
import com.ttq.action.ActionContext;
import com.ttq.action.ActionFailedException;
import com.ttq.action.ActionRegistry;
import com.ttq.action.ActionResult;
import com.ttq.conversation.ChatMessage;
import com.ttq.conversation.ChatMessageRepository;
import com.ttq.conversation.Conversation;
import com.ttq.conversation.ConversationRepository;
import com.ttq.conversation.MessageRole;
import com.ttq.engine.TransitionPolicy.TurnOutcome;
import com.ttq.llm.TurnContext;
import com.ttq.llm.TurnDecision;
import com.ttq.llm.TurnGenerator;
import com.ttq.process.FieldOptionsResolver;
import com.ttq.process.ProcessDefinition;
import com.ttq.process.ProcessRegistry;
import com.ttq.process.StateDefinition;
import com.ttq.process.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs conversations through their process.
 *
 * <p>The model call happens outside any transaction so a slow LLM response doesn't hold a
 * database connection. Concurrent turns on the same conversation are detected via the
 * conversation's version and rejected.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    static final String OPENING_PROMPT =
            "[The user has just opened the conversation. Greet them and start working on the current objective.]";

    private final ProcessRegistry processes;
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final TurnGenerator turnGenerator;
    private final TransitionPolicy transitionPolicy;
    private final ActionRegistry actions;
    private final TransactionTemplate tx;
    private final ChatbotProperties properties;
    private final FieldOptionsResolver fieldOptions;

    public ConversationService(ProcessRegistry processes, ConversationRepository conversations,
                               ChatMessageRepository messages, TurnGenerator turnGenerator,
                               TransitionPolicy transitionPolicy, ActionRegistry actions,
                               TransactionTemplate tx, ChatbotProperties properties,
                               FieldOptionsResolver fieldOptions) {
        this.processes = processes;
        this.conversations = conversations;
        this.messages = messages;
        this.turnGenerator = turnGenerator;
        this.transitionPolicy = transitionPolicy;
        this.actions = actions;
        this.tx = tx;
        this.properties = properties;
        this.fieldOptions = fieldOptions;
    }

    public StartResult start(String processId) {
        ProcessDefinition process = processes.get(processId);
        Conversation conversation = new Conversation(process.id(), process.initialState());

        String greeting = process.greeting();
        if (greeting == null || greeting.isBlank()) {
            greeting = turnGenerator.generate(new TurnContext(process, process.state(process.initialState()),
                    Map.of(), List.of(), OPENING_PROMPT, List.of())).reply();
        }

        String reply = greeting.strip();
        return tx.execute(status -> {
            Conversation saved = conversations.save(conversation);
            ChatMessage message = messages.save(
                    new ChatMessage(saved.getId(), MessageRole.ASSISTANT, reply, saved.getCurrentState()));
            return new StartResult(saved, message);
        });
    }

    public TurnResult sendMessage(UUID conversationId, String content) {
        Snapshot snapshot = tx.execute(status -> {
            Conversation c = find(conversationId);
            if (!c.isActive()) {
                throw new ConversationClosedException(conversationId);
            }
            List<ChatMessage> history = new ArrayList<>(messages.findByConversationIdOrderByIdDesc(
                    conversationId, Limit.of(properties.historyWindow())));
            Collections.reverse(history);
            return new Snapshot(c, history);
        });

        Conversation conversation = snapshot.conversation();
        String userMessageState = conversation.getCurrentState();
        ProcessDefinition process = processes.get(conversation.getProcessId());
        String stateBefore = conversation.getCurrentState();
        Map<String, Object> collectedData = conversation.getCollectedData();

        TurnDecision decision = turnGenerator.generate(new TurnContext(process, process.state(stateBefore),
                collectedData, snapshot.history(), content, switchTargets(process)));

        // The user wants another process: hand the conversation over and let that process answer the
        // same message from its start, so it can pick up anything the message already gives, such
        // as a reference number. It cannot switch again in the same turn.
        ProcessDefinition switchedFrom = null;
        ProcessDefinition target = switchTarget(conversationId, process, decision);
        if (target != null) {
            log.info("Conversation {}: switching from process '{}' to '{}'", conversationId, process.id(), target.id());
            switchedFrom = process;
            process = target;
            stateBefore = target.initialState();
            collectedData = Map.of();
            decision = turnGenerator.generate(new TurnContext(process, process.state(stateBefore),
                    collectedData, snapshot.history(), content, List.of()));
        }

        TurnOutcome outcome = transitionPolicy.apply(process, stateBefore, collectedData, decision);
        // The model wrote its reply before seeing what the values it just gave look up, such as the
        // products a search finds. Ask again with those in view, from wherever the first answer led.
        if (needsSecondLook(process, stateBefore, outcome)) {
            Map<String, Object> seen = new LinkedHashMap<>(collectedData);
            seen.putAll(outcome.acceptedFields());
            log.info("Conversation {}: second look from state '{}' after collecting {}", conversationId,
                    outcome.state().id(), outcome.acceptedFields().keySet());
            decision = turnGenerator.generate(new TurnContext(process, outcome.state(), seen, snapshot.history(),
                    content, List.of()));
            outcome = outcome.followedBy(transitionPolicy.apply(process, outcome.state().id(), seen, decision));
        }
        if (outcome.note() != null) {
            log.warn("Conversation {}: {}", conversationId, outcome.note());
        }

        // Actions run outside the transaction, like the model call, and may cancel the transition.
        Set<String> produced = new HashSet<>();
        TurnOutcome entered = runEntryActions(conversationId, process, collectedData, outcome, produced);
        TurnOutcome settled = moveOnWhileSettled(conversationId, process, collectedData, entered, produced);
        if (settled != entered) {
            // The reply was written for a state the conversation did not stay in; write it for the one it ended in.
            Map<String, Object> seen = new LinkedHashMap<>(collectedData);
            seen.putAll(settled.acceptedFields());
            decision = turnGenerator.generate(new TurnContext(process, settled.state(), seen, snapshot.history(),
                    content, List.of()));
        }
        TurnOutcome finalOutcome = addFollowUps(process, collectedData, settled);
        String replyText = replyText(decision, finalOutcome);
        ProcessDefinition newProcess = switchedFrom == null ? null : process;
        String switchedFromId = switchedFrom == null ? null : switchedFrom.id();
        // The reply an action failure replaced asks to try again, not for the field the model named.
        String asking = finalOutcome.actionFailed() ? null : decision.asking();

        return tx.execute(status -> {
            Conversation current = find(conversationId);
            if (current.getVersion() != conversation.getVersion()) {
                throw new ConcurrentTurnException(conversationId);
            }
            current.touch();
            if (newProcess != null) {
                current.switchProcess(newProcess.id(), newProcess.initialState());
            }
            current.putAllData(finalOutcome.acceptedFields());
            current.moveTo(finalOutcome.state().id(), finalOutcome.state().terminal());
            messages.save(new ChatMessage(conversationId, MessageRole.USER, content, userMessageState));
            ChatMessage reply = messages.save(
                    new ChatMessage(conversationId, MessageRole.ASSISTANT, replyText, current.getCurrentState()));
            conversations.saveAndFlush(current);
            return new TurnResult(current, reply, finalOutcome, switchedFromId, asking);
        });
    }

    /**
     * True when the turn started in a state asking for a second look and collected a value that some
     * field's options are looked up with. Not when it entered a state with actions or a final state,
     * whose own messages follow the reply.
     */
    private boolean needsSecondLook(ProcessDefinition process, String stateBefore, TurnOutcome outcome) {
        if (!process.state(stateBefore).secondLook() || outcome.acceptedFields().isEmpty()
                || outcome.state().terminal() || (outcome.transitioned() && outcome.state().hasActions())) {
            return false;
        }
        return process.fields().stream()
                .anyMatch(f -> fieldOptions.dependsOn(f, outcome.acceptedFields().keySet()));
    }

    private List<ProcessDefinition> switchTargets(ProcessDefinition process) {
        return process.switchTo().stream().map(processes::find).flatMap(Optional::stream).toList();
    }

    /** The process the model asked to hand over to, or null to stay; a process not in switchTo is refused. */
    private ProcessDefinition switchTarget(UUID conversationId, ProcessDefinition process, TurnDecision decision) {
        String requested = decision.switchProcess() == null ? null : decision.switchProcess().strip();
        if (requested == null || requested.isEmpty() || requested.equalsIgnoreCase("null")
                || requested.equals(process.id())) {
            return null;
        }
        if (!process.switchTo().contains(requested)) {
            log.warn("Conversation {}: rejected switch to '{}': not in switchTo of '{}'",
                    conversationId, requested, process.id());
            return null;
        }
        return processes.find(requested).orElse(null);
    }

    /**
     * Runs the actions of the state just entered, in order, each seeing what the earlier ones
     * returned. What they return is merged into the accepted fields; if one fails, the rest are
     * skipped and the transition is cancelled so the conversation stays put and the user is told
     * the truth rather than the model's optimistic reply.
     */
    private TurnOutcome runEntryActions(UUID conversationId, ProcessDefinition process,
                                        Map<String, Object> collectedData, TurnOutcome outcome,
                                        Set<String> producedFields) {
        if (!outcome.transitioned() || !outcome.state().hasActions()) {
            return outcome;
        }
        Map<String, Object> merged = new LinkedHashMap<>(collectedData);
        merged.putAll(outcome.acceptedFields());
        TurnOutcome result = outcome;
        for (String action : outcome.state().actions()) {
            try {
                ActionResult produced = actions.run(action, new ActionContext(conversationId, process, merged));
                log.info("Conversation {}: action '{}' produced {}", conversationId, action, produced.fields().keySet());
                merged.putAll(produced.fields());
                producedFields.addAll(produced.fields().keySet());
                result = result.with(produced);
            } catch (RuntimeException e) {
                String reason = "Action '%s' failed: %s".formatted(action, e.getMessage());
                if (e instanceof ActionFailedException failed && failed.userMessage() != null) {
                    // Refused because of something the user can fix, such as a wrong code.
                    log.warn("Conversation {}: {}", conversationId, reason);
                    return result.cancelled(process.state(outcome.previousState()), merged, reason,
                            failed.userMessage(), failed.fieldsToClear());
                }
                log.error("Conversation {}: action '{}' failed", conversationId, action, e);
                return result.cancelled(process.state(outcome.previousState()), merged, reason);
            }
        }
        return result;
    }

    /**
     * Moves the conversation on while the state it just entered has nothing left to ask: the state
     * auto-advances, its own actions collected every field it requires ({@code produced} names what
     * they produced), and exactly one forward transition is eligible. The next state's actions run
     * too. Stops when an action fails.
     */
    private TurnOutcome moveOnWhileSettled(UUID conversationId, ProcessDefinition process,
                                           Map<String, Object> collectedData, TurnOutcome outcome,
                                           Set<String> produced) {
        TurnOutcome current = outcome;
        // At most once per state, so a loop in the definition cannot keep it going.
        for (int hops = 0; hops < process.states().size(); hops++) {
            StateDefinition state = current.state();
            if (!current.transitioned() || current.actionFailed() || !state.autoAdvance() || state.terminal()
                    || !produced.containsAll(state.requiredFields())) {
                return current;
            }
            Map<String, Object> merged = new LinkedHashMap<>(collectedData);
            merged.putAll(current.acceptedFields());
            List<Transition> forward = process.eligibleTransitions(state, merged).stream()
                    .filter(t -> !t.back()).toList();
            if (forward.size() != 1) {
                return current;
            }
            log.info("Conversation {}: nothing to ask in '{}', moving on to '{}'", conversationId, state.id(),
                    forward.getFirst().state());
            produced = new HashSet<>();
            current = runEntryActions(conversationId, process, collectedData,
                    current.advancedTo(process.state(forward.getFirst().state()), merged), produced);
        }
        return current;
    }

    /**
     * Adds the follow-up lines of the state just entered whose conditions hold, judged on the data
     * after its actions ran. Nothing is added when an action failed, since the state was not entered.
     */
    private static TurnOutcome addFollowUps(ProcessDefinition process, Map<String, Object> collectedData,
                                            TurnOutcome outcome) {
        if (!outcome.transitioned() || outcome.actionFailed()) {
            return outcome;
        }
        Map<String, Object> merged = new LinkedHashMap<>(collectedData);
        merged.putAll(outcome.acceptedFields());
        TurnOutcome result = outcome;
        for (String message : process.followUpMessages(outcome.state(), merged)) {
            result = result.with(new ActionResult(Map.of(), message));
        }
        return result;
    }

    private String replyText(TurnDecision decision, TurnOutcome outcome) {
        if (outcome.actionFailed()) {
            return outcome.actionMessage() != null ? outcome.actionMessage() : properties.actionFailureMessage();
        }
        String reply = decision.reply().strip();
        return outcome.actionMessage() == null ? reply : reply + "\n\n" + outcome.actionMessage();
    }

    public Conversation get(UUID conversationId) {
        return find(conversationId);
    }

    public List<ChatMessage> messages(UUID conversationId) {
        find(conversationId);
        return messages.findByConversationIdOrderByIdAsc(conversationId);
    }

    private Conversation find(UUID id) {
        return conversations.findById(id).orElseThrow(() -> new ConversationNotFoundException(id));
    }

    private record Snapshot(Conversation conversation, List<ChatMessage> history) {
    }

    public record StartResult(Conversation conversation, ChatMessage greeting) {
    }

    /**
     * @param switchedFrom id of the process the conversation was handed over from this turn, or null
     * @param asking       name of the field the model said its reply asks for, or null; the values
     *                     a client may offer for it are worked out from the process, not from here
     */
    public record TurnResult(Conversation conversation, ChatMessage reply, TurnOutcome outcome, String switchedFrom,
                             String asking) {
    }
}
