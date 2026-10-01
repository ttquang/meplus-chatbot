package com.ttq.reference;

import com.ttq.lead.CustomerType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference data for the chatbot: which loan purposes a customer type may choose. The chatbot
 * reads this through the API catalog, so changing a row changes what the assistant offers.
 */
@RestController
@RequestMapping("/api/loan-purposes")
public class LoanPurposeController {

    private static final Logger log = LoggerFactory.getLogger(LoanPurposeController.class);

    private final LoanPurposeRepository purposes;

    public LoanPurposeController(LoanPurposeRepository purposes) {
        this.purposes = purposes;
    }

    /** @param customerType e.g. SOLE_PROPRIETORSHIP; every type's list is returned when it is omitted */
    @GetMapping
    @Transactional(readOnly = true)
    public List<LoanPurposeView> list(@RequestParam(required = false) CustomerType customerType) {
        List<LoanPurpose> rows = customerType == null
                ? purposes.findAll().stream().filter(LoanPurpose::isActive).toList()
                : purposes.findByCustomerTypeAndActiveTrueOrderBySortOrderAsc(customerType);
        return rows.stream().map(LoanPurposeView::of).toList();
    }

    public record LoanPurposeView(String code, String label, CustomerType customerType) {

        static LoanPurposeView of(LoanPurpose purpose) {
            return new LoanPurposeView(purpose.getCode(), purpose.getLabel(), purpose.getCustomerType());
        }
    }

    /**
     * Seeds each customer type that has no purposes yet, so the process works out of the box and a
     * customer type added later gets a starting list without touching the others.
     *
     * <p>The starting list is the "Loan Purpose" section of the BSP Standard Business Loan Application
     * Form (SBLAF, Circular No. 1156), which is the same on the Individual/Sole-Proprietorship form and
     * the Cooperative/Partnership/Corporation form. It is kept per customer type so a lender can
     * narrow or extend one type's list in the table.
     */
    @org.springframework.stereotype.Component
    static class Seeder {

        /** code, label, in the order they appear on the form. */
        static final List<String[]> SBLAF_PURPOSES = List.of(
                new String[] {"WORKING_CAPITAL", "Working capital (including receivables and inventory financing)"},
                new String[] {"REAL_ESTATE_CONSTRUCTION", "Construction/Development of real estate"},
                new String[] {"REAL_ESTATE_ACQUISITION", "Acquisition of real estate"},
                new String[] {"LOAN_TAKEOUT_REFINANCING", "Loan takeout/refinancing"},
                new String[] {"BUSINESS_EXPANSION", "Business expansion"},
                new String[] {"EQUIPMENT_MOTOR_VEHICLE", "Purchase of equipment/motor vehicles"},
                new String[] {"BIOLOGICAL_ASSET",
                        "Purchase of biological asset (e.g., livestock, poultry, fish stock, crops, trees)"},
                new String[] {"OTHER", "Others"});

        private final LoanPurposeRepository purposes;

        Seeder(LoanPurposeRepository purposes) {
            this.purposes = purposes;
        }

        @PostConstruct
        @Transactional
        void seed() {
            List<LoanPurpose> rows = new ArrayList<>();
            for (CustomerType type : CustomerType.values()) {
                if (purposes.existsByCustomerType(type)) {
                    continue;
                }
                for (int i = 0; i < SBLAF_PURPOSES.size(); i++) {
                    String[] purpose = SBLAF_PURPOSES.get(i);
                    rows.add(new LoanPurpose(type, purpose[0], purpose[1], i + 1));
                }
            }
            if (!rows.isEmpty()) {
                purposes.saveAll(rows);
                log.info("Seeded {} SBLAF loan purposes", rows.size());
            }
        }
    }
}
