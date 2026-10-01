package com.ttq.lead;

public enum LeadType {
    INDIVIDUAL, COMPANY;

    /** Prefix used in ticket numbers, e.g. {@code LD-IND-000042}. */
    String ticketPrefix() {
        return this == INDIVIDUAL ? "IND" : "COM";
    }
}
