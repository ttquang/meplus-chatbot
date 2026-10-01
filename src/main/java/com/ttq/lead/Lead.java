package com.ttq.lead;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A captured loan lead. Individual and company leads share one table. The business columns are
 * required for a company lead and optional for an individual, whose business may not have started.
 */
@Entity
@Table(name = "lead_capture", uniqueConstraints = {
        @UniqueConstraint(columnNames = "ticketNumber"),
        // One lead per conversation, so a retried submission cannot create a second one.
        @UniqueConstraint(columnNames = "conversationId")})
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lead_seq")
    @SequenceGenerator(name = "lead_seq", sequenceName = "lead_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String ticketNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadType type;

    /** Legal form of the borrower. Nullable so the column can be added to existing rows. */
    @Enumerated(EnumType.STRING)
    private CustomerType customerType;

    /** The conversation that captured this lead, or null when the API was called directly. */
    private UUID conversationId;

    @Column(nullable = false)
    private String contactName;

    @Column(nullable = false)
    private String loanPurpose;

    /** What the loan is for in the customer's words, mainly for the OTHER purpose. */
    @Column(length = 500)
    private String loanPurposeDetails;

    @Column(nullable = false)
    private BigDecimal loanAmount;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String city;

    private String preferredContactTime;

    // Business profile; for an individual, companyName is the trade name, if any
    private String companyName;

    private String industry;

    private String totalAssetSize;

    /** Nullable so the column can be added to existing rows; those read as {@link LeadStatus#SUBMITTED}. */
    @Enumerated(EnumType.STRING)
    private LeadStatus status;

    private Instant statusUpdatedAt;

    private Instant createdAt;

    protected Lead() {
    }

    private Lead(CustomerType customerType, UUID conversationId, String contactName, String loanPurpose,
                 BigDecimal loanAmount, String phoneNumber, String email, String city,
                 String preferredContactTime) {
        this.type = customerType.leadType();
        this.customerType = customerType;
        this.conversationId = conversationId;
        this.contactName = contactName;
        this.loanPurpose = loanPurpose;
        this.loanAmount = loanAmount;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.city = city;
        this.preferredContactTime = preferredContactTime;
    }

    static Lead individual(LeadApiModels.CreateIndividualLeadRequest request) {
        Lead lead = new Lead(CustomerType.INDIVIDUAL, request.conversationId(), request.contactName(),
                request.loanPurpose(), request.loanAmount(), request.phoneNumber(), request.email(),
                request.city(), request.preferredContactTime());
        lead.loanPurposeDetails = request.loanPurposeDetails();
        lead.companyName = request.companyName();
        lead.industry = request.industry();
        lead.totalAssetSize = request.totalAssetSize();
        return lead;
    }

    static Lead company(LeadApiModels.CreateCompanyLeadRequest request) {
        Lead lead = new Lead(request.customerType(), request.conversationId(), request.contactName(),
                request.loanPurpose(), request.loanAmount(), request.phoneNumber(), request.email(),
                request.city(), request.preferredContactTime());
        lead.loanPurposeDetails = request.loanPurposeDetails();
        lead.companyName = request.companyName();
        lead.industry = request.industry();
        lead.totalAssetSize = request.totalAssetSize();
        return lead;
    }

    /** The id comes from the sequence before this runs, so the ticket number can be derived from it. */
    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (status == null) {
            status = LeadStatus.SUBMITTED;
            statusUpdatedAt = createdAt;
        }
        if (ticketNumber == null) {
            ticketNumber = "LD-%s-%06d".formatted(type.ticketPrefix(), id);
        }
    }

    void changeStatus(LeadStatus newStatus) {
        if (newStatus != getStatus()) {
            status = newStatus;
            statusUpdatedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public LeadType getType() {
        return type;
    }

    /** Null for a business lead captured before customer types were recorded. */
    public CustomerType getCustomerType() {
        return customerType == null && type == LeadType.INDIVIDUAL ? CustomerType.INDIVIDUAL : customerType;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public String getContactName() {
        return contactName;
    }

    public String getLoanPurpose() {
        return loanPurpose;
    }

    public String getLoanPurposeDetails() {
        return loanPurposeDetails;
    }

    public BigDecimal getLoanAmount() {
        return loanAmount;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public String getCity() {
        return city;
    }

    public String getPreferredContactTime() {
        return preferredContactTime;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getIndustry() {
        return industry;
    }

    public String getTotalAssetSize() {
        return totalAssetSize;
    }

    public LeadStatus getStatus() {
        return status == null ? LeadStatus.SUBMITTED : status;
    }

    public Instant getStatusUpdatedAt() {
        return statusUpdatedAt == null ? createdAt : statusUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
