package com.ttq.embedding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** The embedding of one product category, kept like a {@link ProductEmbedding}; linked by the category's code. */
@Entity
@Table(name = "medical_supply_product_category_embedding")
public class ProductCategoryEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String categoryCode;

    @Column(nullable = false, length = 2000)
    private String content;

    /** SHA-256 of the model name and content. */
    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, length = 100)
    private String model;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private float[] embedding;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProductCategoryEmbedding() {
    }

    public ProductCategoryEmbedding(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public void update(String content, String contentHash, String model, float[] embedding) {
        this.content = content;
        this.contentHash = contentHash;
        this.model = model;
        this.embedding = embedding;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getContent() {
        return content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getModel() {
        return model;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
