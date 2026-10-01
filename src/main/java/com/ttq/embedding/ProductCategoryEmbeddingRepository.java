package com.ttq.embedding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductCategoryEmbeddingRepository extends JpaRepository<ProductCategoryEmbedding, Long> {

    List<ProductCategoryEmbedding> findByModel(String model);
}
