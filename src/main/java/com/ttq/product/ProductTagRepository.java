package com.ttq.product;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductTagRepository extends JpaRepository<ProductTag, Long> {

    @EntityGraph(attributePaths = {"product", "tag"})
    List<ProductTag> findByProductCodeIn(Collection<String> productCodes);
}
