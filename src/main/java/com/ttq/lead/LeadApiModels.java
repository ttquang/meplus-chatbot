package com.ttq.lead;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Request and response bodies of the lead API. */
public final class LeadApiModels {

    private LeadApiModels() {
    }

    /**
     * A lead for an {@link CustomerType#INDIVIDUAL}: a natural person doing or proposing to do business.
     * The business details are optional, since the business may not have started yet.
     *
     * @param loanPurposeDetails what the loan is for in the customer's words, for the SBLAF
     *                           "Others (please specify)" purpose; optional
     * @param companyName        trade name the person does business under, if any
     * @param conversationId     conversation the lead came from; a second call with the same id
     *                           returns the existing lead instead of creating another one
     */
    public record CreateIndividualLeadRequest(
            @NotBlank @Size(max = 200) String contactName,
            @NotBlank @Size(max = 50) String loanPurpose,
            @Size(max = 500) String loanPurposeDetails,
            @NotNull @DecimalMin("0.0") BigDecimal loanAmount,
            @NotBlank @Size(max = 50) String phoneNumber,
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(max = 100) String city,
            @Size(max = 20) String preferredContactTime,
            @Size(max = 200) String companyName,
            @Size(max = 50) String industry,
            @Size(max = 50) String totalAssetSize,
            UUID conversationId) {
    }

    /**
     * A lead for any customer type other than {@link CustomerType#INDIVIDUAL}.
     *
     * @param companyName registered name of the business; for a sole proprietorship, the business
     *                    name registered with the DTI
     */
    public record CreateCompanyLeadRequest(
            @NotNull CustomerType customerType,
            @NotBlank @Size(max = 200) String companyName,
            @NotBlank @Size(max = 50) String industry,
            @NotBlank @Size(max = 50) String totalAssetSize,
            @NotBlank @Size(max = 200) String contactName,
            @NotBlank @Size(max = 50) String loanPurpose,
            @Size(max = 500) String loanPurposeDetails,
            @NotNull @DecimalMin("0.0") BigDecimal loanAmount,
            @NotBlank @Size(max = 50) String phoneNumber,
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(max = 100) String city,
            @Size(max = 20) String preferredContactTime,
            UUID conversationId) {

        @AssertTrue(message = "customerType must not be INDIVIDUAL; use the individual lead api")
        public boolean isCompanyCustomerType() {
            return customerType == null || customerType.leadType() == LeadType.COMPANY;
        }
    }

    /**
     * Asks for an application's status. The phone number must be the one the application was made
     * with, so a reference number on its own is not enough to see someone else's application.
     */
    public record StatusCheckRequest(
            @NotBlank @Size(max = 50) String ticketNumber,
            @NotBlank @Size(max = 50) String phoneNumber) {
    }

    public record UpdateStatusRequest(@NotNull LeadStatus status) {
    }

    /**
     * What a customer may see about their application: no contact or loan details, since the
     * caller has only proven the reference number and phone number.
     *
     * @param statusLabel       customer-facing name of the status
     * @param statusDescription customer-facing explanation of what the status means
     * @param completed         true once nothing more will happen to the application
     * @param submittedOn       date the application was made, in Philippine time
     * @param lastUpdatedOn     date the status last changed, in Philippine time
     */
    public record LeadStatusResponse(String ticketNumber, LeadType type, LeadStatus status, String statusLabel,
                                     String statusDescription, boolean completed, LocalDate submittedOn,
                                     LocalDate lastUpdatedOn) {

        private static final ZoneId PHILIPPINES = ZoneId.of("Asia/Manila");

        static LeadStatusResponse of(Lead lead) {
            return new LeadStatusResponse(lead.getTicketNumber(), lead.getType(), lead.getStatus(),
                    lead.getStatus().label(), lead.getStatus().description(), lead.getStatus().completed(),
                    LocalDate.ofInstant(lead.getCreatedAt(), PHILIPPINES),
                    LocalDate.ofInstant(lead.getStatusUpdatedAt(), PHILIPPINES));
        }
    }

    /**
     * @param created false when the lead already existed for this conversation
     */
    public record LeadResponse(Long id, String ticketNumber, LeadType type, CustomerType customerType,
                               boolean created, Instant createdAt) {

        static LeadResponse of(Lead lead, boolean created) {
            return new LeadResponse(lead.getId(), lead.getTicketNumber(), lead.getType(),
                    lead.getCustomerType(), created, lead.getCreatedAt());
        }
    }
}
