package com.ttq.lead;

import com.ttq.lead.LeadApiModels.CreateCompanyLeadRequest;
import com.ttq.lead.LeadApiModels.CreateIndividualLeadRequest;
import com.ttq.lead.LeadApiModels.LeadResponse;
import com.ttq.lead.LeadApiModels.LeadStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LeadServiceTest {

    @Autowired
    LeadService leads;

    @Test
    void createsIndividualAndCompanyLeadsWithTheirOwnTicketPrefix() {
        LeadResponse individual = leads.createIndividual(individualRequest(UUID.randomUUID()));
        assertThat(individual.created()).isTrue();
        assertThat(individual.type()).isEqualTo(LeadType.INDIVIDUAL);
        assertThat(individual.ticketNumber()).startsWith("LD-IND-");

        LeadResponse company = leads.createCompany(new CreateCompanyLeadRequest(CustomerType.COOPERATIVE,
                "Acme Ltd", "RETAIL", "FROM_1M_TO_5M", "Bob Tran", "OTHER", "Buy a rice mill", new BigDecimal("250000"),
                "0900000002", "bob@acme.example", "Hanoi", "MORNING", UUID.randomUUID()));
        assertThat(company.ticketNumber()).startsWith("LD-COM-");
        assertThat(company.type()).isEqualTo(LeadType.COMPANY);
        assertThat(company.customerType()).isEqualTo(CustomerType.COOPERATIVE);
        assertThat(individual.customerType()).isEqualTo(CustomerType.INDIVIDUAL);

        assertThat(leads.findByTicketNumber(individual.ticketNumber()))
                .get().extracting(LeadResponse::type).isEqualTo(LeadType.INDIVIDUAL);
    }

    @Test
    void theSameConversationNeverCreatesTwoLeads() {
        UUID conversationId = UUID.randomUUID();

        LeadResponse first = leads.createIndividual(individualRequest(conversationId));
        LeadResponse retry = leads.createIndividual(individualRequest(conversationId));

        assertThat(first.created()).isTrue();
        assertThat(retry.created()).isFalse();
        assertThat(retry.ticketNumber()).isEqualTo(first.ticketNumber());
        assertThat(retry.id()).isEqualTo(first.id());
    }

    @Test
    void statusIsOnlyGivenWhenThePhoneNumberMatchesTheApplication() {
        LeadResponse lead = leads.createIndividual(new CreateIndividualLeadRequest("Dan Cruz", "EQUIPMENT_MOTOR_VEHICLE", null,
                new BigDecimal("15000"), "+639171234567", "dan@example.com", "Cebu", "EVENING", null, null, null, UUID.randomUUID()));

        LeadStatusResponse status = leads.checkStatus(lead.ticketNumber(), "+639171234567").orElseThrow();
        assertThat(status.status()).isEqualTo(LeadStatus.SUBMITTED);
        assertThat(status.statusLabel()).isEqualTo("Submitted");
        assertThat(status.completed()).isFalse();
        assertThat(status.submittedOn()).isEqualTo(status.lastUpdatedOn());

        // However the customer writes the same number, and the reference in any case.
        assertThat(leads.checkStatus(lead.ticketNumber().toLowerCase(), "0917 123 4567")).isPresent();
        assertThat(leads.checkStatus(lead.ticketNumber(), "63-917-123-4567")).isPresent();

        // A different phone looks exactly like an unknown reference.
        assertThat(leads.checkStatus(lead.ticketNumber(), "+639170000000")).isEmpty();
        assertThat(leads.checkStatus("LD-IND-999999", "+639171234567")).isEmpty();
    }

    @Test
    void updatingTheStatusIsWhatTheCustomerSeesNext() {
        LeadResponse lead = leads.createIndividual(individualRequest(UUID.randomUUID()));

        assertThat(leads.updateStatus(lead.ticketNumber(), LeadStatus.IN_REVIEW)).isPresent();

        LeadStatusResponse status = leads.checkStatus(lead.ticketNumber(), "0900000001").orElseThrow();
        assertThat(status.status()).isEqualTo(LeadStatus.IN_REVIEW);
        assertThat(status.statusDescription()).isEqualTo(LeadStatus.IN_REVIEW.description());
        assertThat(leads.updateStatus("LD-IND-999999", LeadStatus.APPROVED)).isEmpty();
    }

    private static CreateIndividualLeadRequest individualRequest(UUID conversationId) {
        return new CreateIndividualLeadRequest("Ann Lee", "EQUIPMENT_MOTOR_VEHICLE", null, new BigDecimal("15000"), "0900000001",
                "ann@example.com", "Da Nang", "EVENING", null, null, null, conversationId);
    }
}
