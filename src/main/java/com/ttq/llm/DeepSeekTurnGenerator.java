package com.ttq.llm;

import com.ttq.conversation.ChatMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest.Thinking;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeepSeekTurnGenerator implements TurnGenerator {

    private final ChatClient chatClient;
    private final TurnPromptFactory promptFactory;

    public DeepSeekTurnGenerator(ChatClient.Builder chatClientBuilder, TurnPromptFactory promptFactory) {
        this.chatClient = chatClientBuilder.build();
        this.promptFactory = promptFactory;
    }

    @Override
    public TurnDecision generate(TurnContext ctx) {
        TurnDecision decision;
        try {
            decision = chatClient.prompt()
                    .system(promptFactory.systemPrompt(ctx))
                    .messages(toMessages(ctx.history()))
                    .user(ctx.userMessage())
                    .options(DeepSeekChatOptions.builder()
                            .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                            // Non-thinking mode: faster and cheaper for turn-by-turn extraction.
                            .thinking(Thinking.DISABLED))
                    .call()
                    .entity(TurnDecision.class);
        } catch (RuntimeException e) {
            throw new LlmException("DeepSeek call failed: " + e.getMessage(), e);
        }
        if (decision == null || decision.reply() == null || decision.reply().isBlank()) {
            throw new LlmException("DeepSeek returned an empty reply", null);
        }
        return decision;
    }

    private static List<Message> toMessages(List<ChatMessage> history) {
        return history.stream()
                .<Message>map(m -> switch (m.getRole()) {
                    case USER -> new UserMessage(m.getContent());
                    case ASSISTANT -> new AssistantMessage(m.getContent());
                })
                .toList();
    }
}
