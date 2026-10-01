package com.ttq.lead;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    Optional<Lead> findByConversationId(UUID conversationId);

    Optional<Lead> findByTicketNumber(String ticketNumber);
}
