package com.ttq.reference;

import com.ttq.lead.CustomerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A loan purpose offered to a customer type. Held as data so the list can change without
 * editing a process definition or redeploying.
 *
 * <p>Its own table rather than the earlier {@code loan_purpose}, which was keyed by lead type
 * (INDIVIDUAL or COMPANY) and held values this enum cannot read.
 */
@Entity
@Table(name = "customer_loan_purpose",
        uniqueConstraints = @UniqueConstraint(columnNames = {"customerType", "code"}))
public class LoanPurpose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerType customerType;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int sortOrder;

    protected LoanPurpose() {
    }

    public LoanPurpose(CustomerType customerType, String code, String label, int sortOrder) {
        this.customerType = customerType;
        this.code = code;
        this.label = label;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public CustomerType getCustomerType() {
        return customerType;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
