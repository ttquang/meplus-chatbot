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

import java.io.Serializable;

/**
 * One variant of a product, told apart by its attribute values (see {@link ProductVariantAttribute}), linked to the product by the product's code.
 *
 * <p>Serializable because {@link SellableUnit} references it by code rather than by primary key.
 */
@Entity
@Table(name = "medical_supply_product_variant")
public class ProductVariant implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_code", referencedColumnName = "code", nullable = false)
    private Product product;

    /** Further details of this variant, such as the body measurements a size fits; may run over several lines. */
    @Column(name = "additional_desc", length = 2000)
    private String additionalDesc;

    protected ProductVariant() {
    }

    public ProductVariant(String code, Product product) {
        this.code = code;
        this.product = product;
    }

    public ProductVariant(String code, Product product, String additionalDesc) {
        this(code, product);
        this.additionalDesc = additionalDesc;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Product getProduct() {
        return product;
    }

    public String getAdditionalDesc() {
        return additionalDesc;
    }

}
