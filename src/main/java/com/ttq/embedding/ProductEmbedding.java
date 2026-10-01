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

/**
 * The embedding of one product's details: the text it was made from and the vector, plus a hash of
 * that text and the model so an unchanged product is not embedded again. Linked to the product by
 * its code.
 *
 * <p>The vector is a plain float array column, so the database needs no vector extension; similarity
 * is computed in the application, or the column can be cast to pgvector where that is installed.
 */
@Entity
@Table(name = "medical_supply_product_embedding")
public class ProductEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String productCode;

    @Column(nullable = false, length = 20000)
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

    protected ProductEmbedding() {
    }

    public ProductEmbedding(String productCode) {
        this.productCode = productCode;
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

    public String getProductCode() {
        return productCode;
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
