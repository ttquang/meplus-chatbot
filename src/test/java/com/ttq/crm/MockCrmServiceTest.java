package com.ttq.crm;

import com.ttq.crm.LeadNote.NoteType;
import com.ttq.crm.MockCrmService.NoteView;
import com.ttq.lead.LeadApiModels.CreateIndividualLeadRequest;
import com.ttq.lead.LeadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MockCrmServiceTest {

    @Autowired
    MockCrmService crm;

    @Autowired
    LeadService leads;

    @Test
    void notesAreAttachedToTheLeadAndReadBackOldestFirst() {
        String ticket = newLead();

        NoteView first = crm.attach(ticket, NoteType.CUSTOMER_MESSAGE, "CHATBOT", "  Please call after 5pm.  ",
                UUID.randomUUID().toString()).orElseThrow();
        crm.attach(ticket.toLowerCase(), NoteType.INTERNAL, "ADVISOR", "Called, no answer.", null);

        assertThat(first.created()).isTrue();
        assertThat(first.text()).isEqualTo("Please call after 5pm.");
        // Stored under the lead's own reference, however the caller wrote it.
        assertThat(crm.notes(ticket)).get().asList().extracting("leadReference").containsOnly(ticket);
        assertThat(crm.notes(ticket)).get().asList().extracting("text")
                .containsExactly("Please call after 5pm.", "Called, no answer.");
    }

    @Test
    void aRetryWithTheSameKeyAttachesNothingNew() {
        String ticket = newLead();
        String key = UUID.randomUUID().toString();

        NoteView first = crm.attach(ticket, NoteType.CUSTOMER_MESSAGE, "CHATBOT", "Hello", key).orElseThrow();
        NoteView retry = crm.attach(ticket, NoteType.CUSTOMER_MESSAGE, "CHATBOT", "Hello", key).orElseThrow();

        assertThat(retry.created()).isFalse();
        assertThat(retry.noteId()).isEqualTo(first.noteId());
        assertThat(crm.notes(ticket)).get().asList().hasSize(1);
    }

    @Test
    void anUnknownLeadHasNoNotes() {
        assertThat(crm.attach("LD-IND-999999", NoteType.CUSTOMER_MESSAGE, "CHATBOT", "Hello", null)).isEmpty();
        assertThat(crm.notes("LD-IND-999999")).isEmpty();
    }

    private String newLead() {
        return leads.createIndividual(new CreateIndividualLeadRequest("Gil Reyes", "EQUIPMENT_MOTOR_VEHICLE", null, new BigDecimal("20000"),
                "0900000009", "gil@example.com", "Davao", "ANYTIME", null, null, null, UUID.randomUUID())).ticketNumber();
    }
}
