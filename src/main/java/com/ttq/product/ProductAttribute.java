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

/**
 * An attribute a product has, and whether its value is what tells the product's variants apart,
 * with what a customer comparing products needs to know about it.
 */
@Entity
@Table(name = "medical_supply_product_attribute",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_code", "attribute_code"}))
public class ProductAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_code", referencedColumnName = "code", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_code", referencedColumnName = "code", nullable = false)
    private AttributeDefinition attribute;

    @Column(nullable = false)
    private boolean variantDefining;

    /** What the product has for this attribute, e.g. "Có dây". */
    @Column(length = 500)
    private String validValue;

    /** How to choose between the values, e.g. which size fits which measurement. */
    @Column(length = 1000)
    private String guidance;

    /** How the product compares with others of its category on this attribute. */
    @Column(length = 1000)
    private String comparison;

    /** Codes of the products of the same category with another value, separated by semicolons. */
    @Column(length = 500)
    private String relatedProductCodes;

    protected ProductAttribute() {
    }

    public ProductAttribute(Product product, AttributeDefinition attribute, boolean variantDefining) {
        this.product = product;
        this.attribute = attribute;
        this.variantDefining = variantDefining;
    }

    public ProductAttribute(Product product, AttributeDefinition attribute, boolean variantDefining,
                            String validValue, String guidance, String comparison, String relatedProductCodes) {
        this(product, attribute, variantDefining);
        this.validValue = validValue;
        this.guidance = guidance;
        this.comparison = comparison;
        this.relatedProductCodes = relatedProductCodes;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public AttributeDefinition getAttribute() {
        return attribute;
    }

    public boolean isVariantDefining() {
        return variantDefining;
    }

    public String getValidValue() {
        return validValue;
    }

    public String getGuidance() {
        return guidance;
    }

    public String getComparison() {
        return comparison;
    }

    public String getRelatedProductCodes() {
        return relatedProductCodes;
    }
}
