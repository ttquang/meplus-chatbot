package com.ttq.lead;

/**
 * Where an application stands. The label and description are what the customer is told when they
 * check their status, so the chatbot never words a status itself.
 */
public enum LeadStatus {
    SUBMITTED(false, "Submitted",
            "We have received your application and it is waiting to be assigned to a loan advisor."),
    ADVISOR_ASSIGNED(false, "Advisor assigned",
            "A loan advisor has been assigned and will contact you at your preferred time."),
    IN_REVIEW(false, "In review",
            "Your loan advisor is reviewing your application and documents."),
    APPROVED(true, "Approved",
            "Your application has been approved. Your loan advisor will contact you about the next steps."),
    DECLINED(true, "Declined",
            "We are unable to offer a loan at this time. Your loan advisor can explain the decision."),
    CLOSED(true, "Closed",
            "This application has been closed. You are welcome to apply again at any time.");

    private final boolean completed;
    private final String label;
    private final String description;

    LeadStatus(boolean completed, String label, String description) {
        this.completed = completed;
        this.label = label;
        this.description = description;
    }

    /** True once a decision is made or the application is closed; nothing more happens to it. */
    public boolean completed() {
        return completed;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
