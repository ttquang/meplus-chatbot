package com.ttq.lead;

/**
 * The type of applicant-borrower, as defined in BSP Circular No. 1156 on the Standard Business Loan
 * Application Form (SBLAF). Every type borrows for business: the SBLAF does not cover loans for
 * personal needs.
 *
 * <p>{@link #INDIVIDUAL} and {@link #SOLE_PROPRIETORSHIP} apply on the ISP form (Annex A-1), the others
 * on the CPC form (Annex A-2). An individual is captured as a {@link LeadType#INDIVIDUAL} lead and every
 * other type as a {@link LeadType#COMPANY} lead, which picks the ticket prefix and the lead API.
 */
public enum CustomerType {
    /** A natural person doing or proposing to do business, not registered as a sole proprietorship. */
    INDIVIDUAL(LeadType.INDIVIDUAL, "Individual"),
    /** A business structure owned by an individual who has full control and authority over the business (DTI). */
    SOLE_PROPRIETORSHIP(LeadType.COMPANY, "Sole proprietorship"),
    /** An autonomous and duly registered association of persons under R.A. No. 9520 (CDA). */
    COOPERATIVE(LeadType.COMPANY, "Cooperative"),
    /**
     * A juridical entity where two or more individuals combine their capital, property, skills or labor
     * for a lawful business for gain, sharing profits or losses (Civil Code Art. 1767; SEC).
     */
    PARTNERSHIP(LeadType.COMPANY, "Partnership"),
    /** A corporation with a single stockholder under R.A. No. 11232 (SEC). */
    ONE_PERSON_CORPORATION(LeadType.COMPANY, "One-person corporation"),
    /** An artificial being created by operation of law under R.A. No. 11232 (SEC). */
    CORPORATION(LeadType.COMPANY, "Corporation");

    private final LeadType leadType;
    private final String label;

    CustomerType(LeadType leadType, String label) {
        this.leadType = leadType;
        this.label = label;
    }

    /** The kind of lead this customer is captured as, which picks the lead API and ticket prefix. */
    public LeadType leadType() {
        return leadType;
    }

    public String label() {
        return label;
    }
}
