package com.ttq.crm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * A note attached to a lead in the mock CRM, such as a message the customer left for their loan
 * advisor. Stored so advisors can read it later; the real CRM keeps its own.
 */
@Entity
@Table(name = "mock_crm_lead_note", uniqueConstraints =
        // A retried request with the same key returns the note already attached.
        @UniqueConstraint(columnNames = "idempotencyKey"))
public class LeadNote {

    static final int MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mock_crm_lead_note_seq")
    @SequenceGenerator(name = "mock_crm_lead_note_seq", sequenceName = "mock_crm_lead_note_seq", allocationSize = 1)
    private Long id;

    /** Reference number of the lead, e.g. LD-IND-000042. */
    @Column(nullable = false)
    private String leadReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NoteType type;

    /** Where the note came from, e.g. CHATBOT. */
    @Column(nullable = false)
    private String source;

    @Column(nullable = false, length = MAX_LENGTH)
    private String text;

    /** Client-chosen key that makes a retried request safe; null when none was sent. */
    private String idempotencyKey;

    private Instant createdAt;

    protected LeadNote() {
    }

    LeadNote(String leadReference, NoteType type, String source, String text, String idempotencyKey) {
        this.leadReference = leadReference;
        this.type = type;
        this.source = source;
        this.text = text;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getLeadReference() {
        return leadReference;
    }

    public NoteType getType() {
        return type;
    }

    public String getSource() {
        return source;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum NoteType {
        /** Written by the customer for their loan advisor. */
        CUSTOMER_MESSAGE,
        /** Written by staff. */
        INTERNAL
    }
}
