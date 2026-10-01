package com.ttq.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "conversation")
public class Conversation {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String processId;

    @Column(nullable = false)
    private String currentState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationStatus status;

    /** Field values collected so far, keyed by field name. */
    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "text")
    private Map<String, Object> collectedData = new LinkedHashMap<>();

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private long version;

    protected Conversation() {
    }

    public Conversation(String processId, String initialState) {
        this.id = UUID.randomUUID();
        this.processId = processId;
        this.currentState = initialState;
        this.status = ConversationStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Marks the conversation modified so every turn bumps the version, even if nothing else changed. */
    public void touch() {
        updatedAt = Instant.now();
    }

    public void moveTo(String state, boolean terminal) {
        this.currentState = state;
        if (terminal) {
            this.status = ConversationStatus.COMPLETED;
        }
    }

    /**
     * Hands the conversation over to another process, starting afresh at its initial state. Data
     * collected so far is dropped, since it belongs to the previous process's fields.
     */
    public void switchProcess(String processId, String initialState) {
        this.processId = processId;
        this.currentState = initialState;
        this.collectedData = new LinkedHashMap<>();
    }

    public void putAllData(Map<String, Object> values) {
        // Replace rather than mutate so Hibernate's dirty checking sees the change.
        Map<String, Object> updated = new LinkedHashMap<>(collectedData);
        updated.putAll(values);
        this.collectedData = updated;
    }

    public boolean isActive() {
        return status == ConversationStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getProcessId() {
        return processId;
    }

    public String getCurrentState() {
        return currentState;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public Map<String, Object> getCollectedData() {
        return collectedData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
