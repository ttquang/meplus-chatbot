package com.ttq.reference;

import com.ttq.lead.CustomerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanPurposeRepository extends JpaRepository<LoanPurpose, Long> {

    List<LoanPurpose> findByCustomerTypeAndActiveTrueOrderBySortOrderAsc(CustomerType customerType);

    boolean existsByCustomerType(CustomerType customerType);
}
