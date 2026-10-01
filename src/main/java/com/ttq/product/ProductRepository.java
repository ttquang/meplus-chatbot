package com.ttq.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = "brand")
    Optional<Product> findByCodeIgnoreCaseAndActiveTrue(String code);

    /** The active ones among the given products, with their brand loaded. */
    @EntityGraph(attributePaths = "brand")
    List<Product> findByCodeInAndActiveTrue(Collection<String> codes);

    /** Active products with their brand loaded, a page at a time. */
    @EntityGraph(attributePaths = "brand")
    Page<Product> findByActiveTrue(Pageable pageable);
}
