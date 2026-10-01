package com.ttq.product;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One tag carried by one product, linked to both by code. A product may carry several tags of the
 * same group, such as a two-layer pouch that is both Đục and Trong suốt.
 */
@Entity
@Table(name = "medical_supply_product_tag",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_code", "tag_code"}))
public class ProductTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_code", referencedColumnName = "code", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_code", referencedColumnName = "code", nullable = false)
    private Tag tag;

    protected ProductTag() {
    }

    public ProductTag(Product product, Tag tag) {
        this.product = product;
        this.tag = tag;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Tag getTag() {
        return tag;
    }
}
