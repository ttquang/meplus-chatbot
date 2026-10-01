package com.ttq.crm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeadNoteRepository extends JpaRepository<LeadNote, Long> {

    Optional<LeadNote> findByIdempotencyKey(String idempotencyKey);

    List<LeadNote> findByLeadReferenceOrderByIdAsc(String leadReference);
}
