package com.ttq.web;

import com.ttq.ChatbotProperties;
import com.ttq.conversation.ChatMessage;
import com.ttq.conversation.Conversation;
import com.ttq.engine.ConversationService;
import com.ttq.engine.ConversationService.StartResult;
import com.ttq.engine.ConversationService.TurnResult;
import com.ttq.process.ChoiceResolver;
import com.ttq.process.ProcessDefinition;
import com.ttq.process.ProcessRegistry;
import com.ttq.process.StateDefinition;
import com.ttq.web.ApiModels.ConversationView;
import com.ttq.web.ApiModels.MessageView;
import com.ttq.web.ApiModels.SendMessageRequest;
import com.ttq.web.ApiModels.StartConversationRequest;
import com.ttq.web.ApiModels.StartConversationResponse;
import com.ttq.web.ApiModels.TurnResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ConversationController {

    private final ConversationService service;
    private final ProcessRegistry processes;
    private final ChoiceResolver choices;
    private final ChatbotProperties properties;

    public ConversationController(ConversationService service, ProcessRegistry processes, ChoiceResolver choices,
                                  ChatbotProperties properties) {
        this.service = service;
        this.processes = processes;
        this.choices = choices;
        this.properties = properties;
    }

    /** All processes, the default one first. */
    @GetMapping("/processes")
    public List<ProcessDefinition> listProcesses() {
        return processes.all().stream()
                .sorted(Comparator.comparing((ProcessDefinition p) -> !p.id().equals(properties.defaultProcess())))
                .toList();
    }

    @GetMapping("/processes/{processId}")
    public ProcessDefinition getProcess(@PathVariable String processId) {
        return processes.get(processId);
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public StartConversationResponse start(@Valid @RequestBody StartConversationRequest request) {
        String processId = request.processId() == null || request.processId().isBlank()
                ? properties.defaultProcess() : request.processId();
        if (processId == null || processId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "processId is required");
        }
        StartResult result = service.start(processId);
        return new StartConversationResponse(guessedView(result.conversation()), MessageView.of(result.greeting()));
    }

    @GetMapping("/conversations/{id}")
    public ConversationView get(@PathVariable UUID id) {
        return guessedView(service.get(id));
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageView> messages(@PathVariable UUID id) {
        return service.messages(id).stream().map(MessageView::of).toList();
    }

    @PostMapping("/conversations/{id}/messages")
    public TurnResponse sendMessage(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest request) {
        TurnResult result = service.sendMessage(id, request.content());
        ChatMessage reply = result.reply();
        return new TurnResponse(
                MessageView.of(reply),
                result.outcome().previousState(),
                result.outcome().transitioned(),
                result.outcome().acceptedFields(),
                result.outcome().ignoredFields(),
                result.outcome().note(),
                result.switchedFrom(),
                view(result.conversation(), result.asking()));
    }

    /** The conversation after a turn, offering the values of the field the model said it asked for. */
    private ConversationView view(Conversation c, String asking) {
        ProcessDefinition process = processes.get(c.getProcessId());
        StateDefinition state = process.state(c.getCurrentState());
        return ConversationView.of(c, process,
                choices.forField(process, state, c.getCollectedData(), asking).orElse(null));
    }

    /**
     * The conversation outside a turn, as when it is picked up again after a reload. There is no
     * model answer to go by, so the values offered are those of the next field still to collect.
     */
    private ConversationView guessedView(Conversation c) {
        ProcessDefinition process = processes.get(c.getProcessId());
        StateDefinition state = process.state(c.getCurrentState());
        return ConversationView.of(c, process,
                choices.forNextMissingField(process, state, c.getCollectedData()).orElse(null));
    }
}
