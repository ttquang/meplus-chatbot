package com.ttq.product;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findByActiveTrue(Sort sort);

    /** The active ones among the given categories. */
    List<ProductCategory> findByCodeInAndActiveTrue(Collection<String> codes);

    Optional<ProductCategory> findByCodeIgnoreCaseAndActiveTrue(String code);
}
