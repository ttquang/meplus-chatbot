package com.ttq.product;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long>,
        JpaSpecificationExecutor<ProductVariant> {

    Optional<ProductVariant> findByCodeIgnoreCase(String code);

    /** A variant of an active product, with the product loaded. */
    @EntityGraph(attributePaths = "product")
    Optional<ProductVariant> findByCodeIgnoreCaseAndProductActiveTrue(String code);

    List<ProductVariant> findByProductCodeOrderByCodeAsc(String productCode);

    /** The variants of the given products, with the product loaded. */
    @EntityGraph(attributePaths = "product")
    List<ProductVariant> findByProductCodeInOrderByCodeAsc(Collection<String> productCodes);
}
