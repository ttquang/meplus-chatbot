package com.ttq.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;

/**
 * A medical supply product in the catalog, linked to its brand by the brand's code.
 *
 * <p>Serializable because {@link ProductVariant} references it by code rather than by primary key.
 */
@Entity
@Table(name = "medical_supply_product",
        indexes = @Index(columnList = "category"))
public class Product implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_code", referencedColumnName = "code", nullable = false)
    private Brand brand;

    @Column(nullable = false)
    private String category;

    @Column(length = 2000)
    private String description;

    /** Unit of measure the product is sold in, e.g. hộp, cái. */
    @Column(length = 50)
    private String uom;

    @Column(nullable = false)
    private boolean active = true;

    protected Product() {
    }

    public Product(String code, String name, Brand brand, String category,
                   String description, String uom) {
        this.code = code;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.description = description;
        this.uom = uom;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Brand getBrand() {
        return brand;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getUom() {
        return uom;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
