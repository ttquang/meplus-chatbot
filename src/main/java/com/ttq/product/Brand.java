package com.ttq.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;

/**
 * A brand of medical supplies. Each {@link Product} belongs to one brand, linked by the brand's code.
 *
 * <p>Serializable because products reference it by code rather than by primary key.
 */
@Entity
@Table(name = "medical_supply_brand")
public class Brand implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, unique = true)
    private String name;

    /** Where the brand comes from; empty for the catalog's catch-all "Khác" (other) brand. */
    private String country;

    protected Brand() {
    }

    public Brand(String code, String name, String country) {
        this.code = code;
        this.name = name;
        this.country = country;
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

    public String getCountry() {
        return country;
    }
}
