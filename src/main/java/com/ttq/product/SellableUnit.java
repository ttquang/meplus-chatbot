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

import java.math.BigDecimal;

/**
 * How a product variant is sold, e.g. a box of 10, linked to the variant by the variant's code.
 * A variant can be sold in several units.
 */
@Entity
@Table(name = "medical_supply_sellable_unit")
public class SellableUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_variant_code", referencedColumnName = "code", nullable = false)
    private ProductVariant productVariant;

    /** The unit a customer buys, e.g. Hộp, Cái. */
    @Column(nullable = false, length = 50)
    private String saleUom;

    /** How many of the product's own unit one sale unit holds, e.g. 10 for a box of 10 pieces. */
    @Column(nullable = false)
    private int quantity;

    /** Retail price of one sale unit in VND; empty when it has not been set. */
    @Column(precision = 15, scale = 0)
    private BigDecimal price;

    protected SellableUnit() {
    }

    public SellableUnit(String code, ProductVariant productVariant, String saleUom, int quantity, BigDecimal price) {
        this.code = code;
        this.productVariant = productVariant;
        this.saleUom = saleUom;
        this.quantity = quantity;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public ProductVariant getProductVariant() {
        return productVariant;
    }

    public String getSaleUom() {
        return saleUom;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
