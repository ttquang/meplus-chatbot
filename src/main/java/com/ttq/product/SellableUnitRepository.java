package com.ttq.product;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SellableUnitRepository extends JpaRepository<SellableUnit, Long>,
        JpaSpecificationExecutor<SellableUnit> {

    Optional<SellableUnit> findByCodeIgnoreCase(String code);

    /** A sellable unit of an active product, with its variant and product loaded. */
    @EntityGraph(attributePaths = "productVariant.product")
    Optional<SellableUnit> findByCodeIgnoreCaseAndProductVariantProductActiveTrue(String code);

    List<SellableUnit> findByProductVariantCodeOrderByCodeAsc(String productVariantCode);

    /** The sellable units of the given products' variants, with variant and product loaded. */
    @EntityGraph(attributePaths = "productVariant.product")
    List<SellableUnit> findByProductVariantProductCodeInOrderByCodeAsc(Collection<String> productCodes);
}
