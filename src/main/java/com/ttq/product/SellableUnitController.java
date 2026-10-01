package com.ttq.product;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The units catalog products are sold in, with their prices. Only units of active products are returned. */
@RestController
@RequestMapping("/api/sellable-units")
public class SellableUnitController {

    private static final Sort BY_CODE = Sort.by("code");

    private final SellableUnitRepository units;

    public SellableUnitController(SellableUnitRepository units) {
        this.units = units;
    }

    /**
     * Both filters are optional, ignore case and combine.
     *
     * @param productVariantCode e.g. PRODUCT-00001-001
     * @param productCode        e.g. PRODUCT-00001, for the units of every variant of the product
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<SellableUnitView> list(@RequestParam(required = false) String productVariantCode,
                                       @RequestParam(required = false) String productCode) {
        return units.findAll(matching(productVariantCode, productCode), BY_CODE).stream()
                .map(SellableUnitView::of)
                .toList();
    }

    /** A sellable unit by code, ignoring case, e.g. PRODUCT-00001-001-U01. */
    @GetMapping("/{code}")
    @Transactional(readOnly = true)
    public ResponseEntity<SellableUnitView> get(@PathVariable String code) {
        return ResponseEntity.of(units.findByCodeIgnoreCaseAndProductVariantProductActiveTrue(code.trim())
                .map(SellableUnitView::of));
    }

    static Specification<SellableUnit> matching(String productVariantCode, String productCode) {
        return (root, query, cb) -> {
            // Fetched rather than only joined, so listing units does not load each variant and product separately.
            @SuppressWarnings("unchecked")
            Join<SellableUnit, ProductVariant> variant =
                    (Join<SellableUnit, ProductVariant>) root.<SellableUnit, ProductVariant>fetch("productVariant");
            @SuppressWarnings("unchecked")
            Join<ProductVariant, Product> product =
                    (Join<ProductVariant, Product>) variant.<ProductVariant, Product>fetch("product");
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(product.get("active")));
            if (productVariantCode != null && !productVariantCode.isBlank()) {
                where.add(cb.equal(cb.lower(variant.get("code")), lower(productVariantCode)));
            }
            if (productCode != null && !productCode.isBlank()) {
                where.add(cb.equal(cb.lower(product.get("code")), lower(productCode)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static String lower(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * @param label for showing the unit as a choice, e.g. "Hộp x5 - 150.000 đ"; the quantity is left out
     *              when it is 1 and the price when there is none
     * @param price in VND; null when the unit has no price yet
     */
    public record SellableUnitView(String code, String label, String productVariantCode, String productCode,
                                   String productName, String saleUom, int quantity, BigDecimal price) {

        private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

        static SellableUnitView of(SellableUnit unit) {
            ProductVariant variant = unit.getProductVariant();
            return new SellableUnitView(unit.getCode(), label(unit), variant.getCode(),
                    variant.getProduct().getCode(), variant.getProduct().getName(), unit.getSaleUom(),
                    unit.getQuantity(), unit.getPrice());
        }

        private static String label(SellableUnit unit) {
            StringBuilder label = new StringBuilder(unit.getSaleUom());
            if (unit.getQuantity() != 1) {
                label.append(" x").append(unit.getQuantity());
            }
            if (unit.getPrice() != null) {
                label.append(" - ").append(String.format(VIETNAMESE, "%,d đ", unit.getPrice().toBigInteger()));
            }
            return label.toString();
        }
    }
}
