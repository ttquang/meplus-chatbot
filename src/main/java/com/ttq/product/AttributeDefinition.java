package com.ttq.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;

/**
 * A kind of attribute products or their variants can have, such as "Kích cỡ" or "Chất liệu", with
 * the values it takes. Serializable because the product and variant attributes refer to it by code.
 */
@Entity
@Table(name = "medical_supply_attribute")
public class AttributeDefinition implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 100)
    private String nameEn;

    /** How the value is written, e.g. "Danh sách", "Số (ml)", "Có/Không". */
    @Column(length = 100)
    private String dataType;

    /** The kinds of product it applies to, in words. */
    @Column(length = 1000)
    private String appliesTo;

    /** The values seen in the catalog, separated by semicolons. */
    @Column(length = 2000)
    private String possibleValues;

    /** Where in the product the value is read from. */
    @Column(name = "derivation_rule", length = 500)
    private String rule;

    protected AttributeDefinition() {
    }

    public AttributeDefinition(String code, String name, String nameEn, String dataType, String appliesTo,
                               String possibleValues, String rule) {
        this.code = code;
        this.name = name;
        this.nameEn = nameEn;
        this.dataType = dataType;
        this.appliesTo = appliesTo;
        this.possibleValues = possibleValues;
        this.rule = rule;
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

    public String getNameEn() {
        return nameEn;
    }

    public String getDataType() {
        return dataType;
    }

    public String getAppliesTo() {
        return appliesTo;
    }

    public String getPossibleValues() {
        return possibleValues;
    }

    public String getRule() {
        return rule;
    }
}
