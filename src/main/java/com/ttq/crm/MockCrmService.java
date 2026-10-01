package com.ttq.crm;

import com.ttq.crm.LeadNote.NoteType;
import com.ttq.lead.LeadApiModels.LeadResponse;
import com.ttq.lead.LeadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stand-in for the lender's CRM, where loan advisors work their leads, until the real one is wired
 * in. Notes are kept in this application's database so an advisor can read them later, and each
 * one is logged.
 *
 * <p>The chatbot only reaches it through the API catalog ({@code attachLeadNote}), so replacing it
 * with the real CRM means changing the catalog url and payload, not the process. It trusts its
 * caller like a CRM trusts an internal integration: the chatbot has already checked that the
 * customer owns the lead before attaching anything.
 */
@Service
public class MockCrmService {

    private static final Logger log = LoggerFactory.getLogger(MockCrmService.class);

    private final LeadService leads;
    private final LeadNoteRepository notes;

    public MockCrmService(LeadService leads, LeadNoteRepository notes) {
        this.leads = leads;
        this.notes = notes;
    }

    /**
     * Attaches a note to a lead. A second call with the same idempotency key returns the note
     * already attached instead of adding another.
     *
     * @return empty when there is no lead with that reference
     */
    @Transactional
    public Optional<NoteView> attach(String leadReference, NoteType type, String source, String text,
                                     String idempotencyKey) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.strip();
        return leads.findByTicketNumber(leadReference).map(lead -> {
            if (key != null) {
                Optional<LeadNote> existing = notes.findByIdempotencyKey(key);
                if (existing.isPresent()) {
                    log.info("[MOCK CRM] note {} already attached for key {}", existing.get().getId(), key);
                    return NoteView.of(existing.get(), false);
                }
            }
            LeadNote saved = notes.saveAndFlush(
                    new LeadNote(lead.ticketNumber(), type, source.strip(), text.strip(), key));
            log.info("[MOCK CRM] {} note {} attached to lead {} from {}", type, saved.getId(), lead.ticketNumber(),
                    saved.getSource());
            return NoteView.of(saved, true);
        });
    }

    /** The notes on a lead, oldest first; empty when there is no lead with that reference. */
    @Transactional(readOnly = true)
    public Optional<List<NoteView>> notes(String leadReference) {
        return leads.findByTicketNumber(leadReference).map(LeadResponse::ticketNumber).map(reference ->
                notes.findByLeadReferenceOrderByIdAsc(reference).stream()
                        .map(note -> NoteView.of(note, false))
                        .toList());
    }

    /** @param created false when the note already existed for the idempotency key */
    public record NoteView(Long noteId, String leadReference, NoteType type, String source, String text,
                           Instant createdAt, boolean created) {

        static NoteView of(LeadNote note, boolean created) {
            return new NoteView(note.getId(), note.getLeadReference(), note.getType(), note.getSource(),
                    note.getText(), note.getCreatedAt(), created);
        }
    }
}
