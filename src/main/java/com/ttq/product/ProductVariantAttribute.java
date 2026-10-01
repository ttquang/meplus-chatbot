package com.ttq.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** The value one variant has for an attribute, e.g. size "M" or material "Cao su". */
@Entity
@Table(name = "medical_supply_product_variant_attribute",
        uniqueConstraints = @UniqueConstraint(columnNames = {"variant_code", "attribute_code"}))
public class ProductVariantAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_code", referencedColumnName = "code", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_code", referencedColumnName = "code", nullable = false)
    private AttributeDefinition attribute;

    @Column(name = "attribute_value", nullable = false, length = 255)
    private String value;

    protected ProductVariantAttribute() {
    }

    public ProductVariantAttribute(ProductVariant variant, AttributeDefinition attribute, String value) {
        this.variant = variant;
        this.attribute = attribute;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public AttributeDefinition getAttribute() {
        return attribute;
    }

    public String getValue() {
        return value;
    }
}
