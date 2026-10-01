package com.ttq.conversation;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdAsc(UUID conversationId);

    /** Newest first; reverse before sending to the model. */
    List<ChatMessage> findByConversationIdOrderByIdDesc(UUID conversationId, Limit limit);
}
