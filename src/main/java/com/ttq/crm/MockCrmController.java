package com.ttq.crm;

import com.ttq.crm.LeadNote.NoteType;
import com.ttq.crm.MockCrmService.NoteView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mock CRM API: notes on a lead, such as a message the customer left for their loan advisor. The
 * chatbot attaches them through the API catalog ({@code attachLeadNote}); advisors read them back.
 */
@RestController
@RequestMapping("/api/mock-crm/leads/{leadReference}/notes")
public class MockCrmController {

    private final MockCrmService crm;

    public MockCrmController(MockCrmService crm) {
        this.crm = crm;
    }

    /** 201 with the new note, 200 when the idempotency key was already used, 404 for an unknown lead. */
    @PostMapping
    public ResponseEntity<NoteView> attach(@PathVariable String leadReference,
                                           @RequestHeader(name = "Idempotency-Key", required = false) String key,
                                           @Valid @RequestBody AttachNoteRequest request) {
        return crm.attach(leadReference, request.type(), request.source(), request.text(), key)
                .map(note -> ResponseEntity.status(note.created() ? HttpStatus.CREATED : HttpStatus.OK).body(note))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The notes on a lead, oldest first, for the loan advisors. */
    @GetMapping
    public ResponseEntity<List<NoteView>> list(@PathVariable String leadReference) {
        return ResponseEntity.of(crm.notes(leadReference));
    }

    public record AttachNoteRequest(
            @NotNull NoteType type,
            @NotBlank @Size(max = 50) String source,
            @NotBlank @Size(max = LeadNote.MAX_LENGTH) String text) {
    }
}
