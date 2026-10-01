package com.ttq.lead;

import com.ttq.lead.LeadApiModels.CreateCompanyLeadRequest;
import com.ttq.lead.LeadApiModels.CreateIndividualLeadRequest;
import com.ttq.lead.LeadApiModels.LeadResponse;
import com.ttq.lead.LeadApiModels.LeadStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates leads. Individual and business leads are created through separate entry points because
 * they carry different information, but they share a ticket series.
 */
@Service
public class LeadService {

    private static final Logger log = LoggerFactory.getLogger(LeadService.class);

    private final LeadRepository leads;

    public LeadService(LeadRepository leads) {
        this.leads = leads;
    }

    @Transactional
    public LeadResponse createIndividual(CreateIndividualLeadRequest request) {
        return existing(request.conversationId())
                .orElseGet(() -> save(Lead.individual(request)));
    }

    @Transactional
    public LeadResponse createCompany(CreateCompanyLeadRequest request) {
        return existing(request.conversationId())
                .orElseGet(() -> save(Lead.company(request)));
    }

    @Transactional(readOnly = true)
    public Optional<LeadResponse> findByTicketNumber(String ticketNumber) {
        return leads.findByTicketNumber(normalizeTicket(ticketNumber)).map(lead -> LeadResponse.of(lead, false));
    }

    /**
     * The status of the application with this reference number, but only when it was made with this
     * phone number. A wrong reference and a wrong phone number look the same, so the answer never
     * reveals which reference numbers exist.
     */
    @Transactional(readOnly = true)
    public Optional<LeadStatusResponse> checkStatus(String ticketNumber, String phoneNumber) {
        return leads.findByTicketNumber(normalizeTicket(ticketNumber))
                .filter(lead -> samePhone(lead.getPhoneNumber(), phoneNumber))
                .map(LeadStatusResponse::of);
    }

    @Transactional
    public Optional<LeadStatusResponse> updateStatus(String ticketNumber, LeadStatus status) {
        return leads.findByTicketNumber(normalizeTicket(ticketNumber)).map(lead -> {
            LeadStatus previous = lead.getStatus();
            lead.changeStatus(status);
            log.info("Lead {} status {} -> {}", lead.getTicketNumber(), previous, status);
            return LeadStatusResponse.of(leads.saveAndFlush(lead));
        });
    }

    /** Accepts "ld-ind-000042" or "LD IND 000042" for LD-IND-000042. */
    static String normalizeTicket(String ticketNumber) {
        return ticketNumber == null ? null
                : ticketNumber.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s_]+", "-");
    }

    /**
     * Compares Philippine mobile numbers whatever way they were written: +63 917 123 4567,
     * 639171234567 and 09171234567 are the same number.
     */
    static boolean samePhone(String stored, String given) {
        String a = nationalNumber(stored);
        return !a.isEmpty() && a.equals(nationalNumber(given));
    }

    private static String nationalNumber(String phoneNumber) {
        String digits = phoneNumber == null ? "" : phoneNumber.replaceAll("\\D", "");
        if (digits.startsWith("63") && digits.length() == 12) {
            return digits.substring(2);
        }
        return digits.startsWith("0") ? digits.substring(1) : digits;
    }

    /** Returns the lead already captured for this conversation, so a retry is not a new lead. */
    private Optional<LeadResponse> existing(UUID conversationId) {
        if (conversationId == null) {
            return Optional.empty();
        }
        return leads.findByConversationId(conversationId).map(lead -> {
            log.info("Conversation {} already has lead {}", conversationId, lead.getTicketNumber());
            return LeadResponse.of(lead, false);
        });
    }

    private LeadResponse save(Lead lead) {
        // The ticket number is derived from the sequence id as the row is persisted.
        Lead saved = leads.saveAndFlush(lead);
        log.info("Created {} lead {} from conversation {}", saved.getCustomerType(), saved.getTicketNumber(),
                saved.getConversationId());
        return LeadResponse.of(saved, true);
    }
}
