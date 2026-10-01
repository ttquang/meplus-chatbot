package com.ttq.embedding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductEmbeddingRepository extends JpaRepository<ProductEmbedding, Long> {

    List<ProductEmbedding> findByModel(String model);

    List<ProductEmbedding> findByProductCodeIn(Collection<String> productCodes);
}
