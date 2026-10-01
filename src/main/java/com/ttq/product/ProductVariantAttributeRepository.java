package com.ttq.product;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductVariantAttributeRepository extends JpaRepository<ProductVariantAttribute, Long> {

    /** The attribute values of the given variants, with the variant and attribute loaded. */
    @EntityGraph(attributePaths = {"variant", "attribute"})
    List<ProductVariantAttribute> findByVariantCodeInOrderByAttributeCodeAsc(Collection<String> variantCodes);

    /** The attribute values of the variants of the given products, with the variant and attribute loaded. */
    @EntityGraph(attributePaths = {"variant", "attribute"})
    List<ProductVariantAttribute> findByVariantProductCodeInOrderByAttributeCodeAsc(Collection<String> productCodes);
}
