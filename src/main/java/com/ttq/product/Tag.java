package com.ttq.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;

/**
 * A feature a customer may look for in a product, such as "Đục" in the group "Độ trong". A tag
 * belongs to one group, and the tags of a group are alternatives: a customer wanting an opaque or a
 * transparent pouch picks from the same group. Products carry tags through {@link ProductTag}.
 *
 * <p>Serializable because product tags reference it by code rather than by primary key.
 */
@Entity
@Table(name = "medical_supply_tag")
public class Tag implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** The question this tag answers, e.g. Độ trong, Đối tượng. */
    @Column(name = "tag_group", nullable = false, length = 100)
    private String group;

    @Column(nullable = false, length = 100)
    private String name;

    /** Other words customers use for this tag, comma separated, e.g. "kín đáo, màu be" for Đục. */
    @Column(length = 500)
    private String synonyms;

    /** Tags are listed, and their groups asked about, in this order. */
    @Column(nullable = false)
    private int sortOrder;

    protected Tag() {
    }

    public Tag(String code, String group, String name, String synonyms, int sortOrder) {
        this.code = code;
        this.group = group;
        this.name = name;
        this.synonyms = synonyms;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getSynonyms() {
        return synonyms;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
