package com.ttq.product;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {

    /** The attributes of the given products, with the attribute loaded. */
    @EntityGraph(attributePaths = "attribute")
    List<ProductAttribute> findByProductCodeIn(Collection<String> productCodes);

    /** The attributes of an active product, ignoring case in its code, with the attribute loaded. */
    @EntityGraph(attributePaths = "attribute")
    List<ProductAttribute> findByProductCodeIgnoreCaseAndProductActiveTrueOrderByAttributeCodeAsc(String productCode);
}
