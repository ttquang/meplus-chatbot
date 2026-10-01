package com.ttq.lead;

import com.ttq.lead.LeadApiModels.CreateCompanyLeadRequest;
import com.ttq.lead.LeadApiModels.CreateIndividualLeadRequest;
import com.ttq.lead.LeadApiModels.LeadResponse;
import com.ttq.lead.LeadApiModels.LeadStatusResponse;
import com.ttq.lead.LeadApiModels.StatusCheckRequest;
import com.ttq.lead.LeadApiModels.UpdateStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leads;

    public LeadController(LeadService leads) {
        this.leads = leads;
    }

    @PostMapping("/individual")
    public ResponseEntity<LeadResponse> createIndividual(@Valid @RequestBody CreateIndividualLeadRequest request) {
        return created(leads.createIndividual(request));
    }

    @PostMapping("/company")
    public ResponseEntity<LeadResponse> createCompany(@Valid @RequestBody CreateCompanyLeadRequest request) {
        return created(leads.createCompany(request));
    }

    @GetMapping("/{ticketNumber}")
    public ResponseEntity<LeadResponse> get(@PathVariable String ticketNumber) {
        return ResponseEntity.of(leads.findByTicketNumber(ticketNumber));
    }

    /**
     * The status of an application, given its reference number and the phone number it was made
     * with. 404 when either is wrong. A POST so the phone number stays out of urls and access logs.
     */
    @PostMapping("/status")
    public ResponseEntity<LeadStatusResponse> checkStatus(@Valid @RequestBody StatusCheckRequest request) {
        return ResponseEntity.of(leads.checkStatus(request.ticketNumber(), request.phoneNumber()));
    }

    /** Moves an application to a new status, for the loan advisors' tools. */
    @PutMapping("/{ticketNumber}/status")
    public ResponseEntity<LeadStatusResponse> updateStatus(@PathVariable String ticketNumber,
                                                           @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.of(leads.updateStatus(ticketNumber, request.status()));
    }

    /** A repeated submission returns 200 with the existing lead rather than creating another. */
    private static ResponseEntity<LeadResponse> created(LeadResponse lead) {
        return ResponseEntity.status(lead.created() ? HttpStatus.CREATED : HttpStatus.OK).body(lead);
    }
}
