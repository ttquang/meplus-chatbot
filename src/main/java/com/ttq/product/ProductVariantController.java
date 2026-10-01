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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The variants of catalog products. Only variants of active products are returned. */
@RestController
@RequestMapping("/api/product-variants")
public class ProductVariantController {

    private static final Sort BY_CODE = Sort.by("code");

    private final ProductVariantRepository variants;
    private final ProductVariantAttributeRepository variantAttributes;
    private final ProductAttributeRepository productAttributes;
    private final SellableUnitRepository units;

    public ProductVariantController(ProductVariantRepository variants,
                                    ProductVariantAttributeRepository variantAttributes,
                                    ProductAttributeRepository productAttributes,
                                    SellableUnitRepository units) {
        this.units = units;
        this.variants = variants;
        this.variantAttributes = variantAttributes;
        this.productAttributes = productAttributes;
    }

    /**
     * Both filters are optional and combine; every variant is returned when none is given.
     *
     * @param productCode e.g. PRODUCT-00001, ignoring case
     * @param category    the category of the product, as its products name it, ignoring case
     * @param attributes  attribute value codes as listed by {@code /api/variant-attributes}, repeated or
     *                    comma separated: a variant must have one of those asked for in each attribute,
     *                    and a value no listed variant has is ignored
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<ProductVariantView> list(@RequestParam(required = false) String productCode,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) List<String> attributes) {
        List<ProductVariant> found = variants.findAll(matching(productCode, category), BY_CODE);
        if (attributes != null && !attributes.isEmpty()) {
            VariantAttributeIndex index = VariantAttributeIndex.of(found, variantAttributes, productAttributes);
            VariantAttributeIndex.Selection selection = index.select(attributes);
            found = found.stream().filter(v -> index.matches(v, selection)).toList();
        }
        return views(found);
    }

    /** A variant by code, ignoring case, e.g. PRODUCT-00001-001. */
    @GetMapping("/{code}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductVariantView> get(@PathVariable String code) {
        return ResponseEntity.of(variants.findByCodeIgnoreCaseAndProductActiveTrue(code.trim())
                .map(variant -> views(List.of(variant)).get(0)));
    }

    /** Views of the given variants, loading their attribute values in two queries however many there are. */
    private List<ProductVariantView> views(List<ProductVariant> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<String> variantCodes = found.stream().map(ProductVariant::getCode).toList();
        List<String> productCodes = found.stream().map(v -> v.getProduct().getCode()).distinct().toList();

        Set<String> defining = new HashSet<>();
        for (ProductAttribute pa : productAttributes.findByProductCodeIn(productCodes)) {
            if (pa.isVariantDefining()) {
                defining.add(pa.getProduct().getCode() + "/" + pa.getAttribute().getCode());
            }
        }
        Map<String, List<ProductVariantAttribute>> valuesByVariant = variantAttributes
                .findByVariantCodeInOrderByAttributeCodeAsc(variantCodes).stream()
                .collect(Collectors.groupingBy(a -> a.getVariant().getCode()));

        // The base price of a variant is that of the unit holding the fewest pieces that has a price.
        Map<String, SellableUnit> baseUnits = units.findByProductVariantProductCodeInOrderByCodeAsc(productCodes)
                .stream()
                .filter(u -> u.getPrice() != null && variantCodes.contains(u.getProductVariant().getCode()))
                .collect(Collectors.toMap(u -> u.getProductVariant().getCode(), u -> u,
                        (a, b) -> b.getQuantity() < a.getQuantity() ? b : a));

        return found.stream().map(variant -> {
            Product product = variant.getProduct();
            List<ProductVariantAttribute> values = valuesByVariant.getOrDefault(variant.getCode(), List.of());
            String label = values.stream()
                    .filter(a -> defining.contains(product.getCode() + "/" + a.getAttribute().getCode()))
                    .map(a -> a.getValue().strip())
                    .collect(Collectors.joining(" / "));
            String shownLabel = label.isEmpty() ? product.getName() : label;
            SellableUnit baseUnit = baseUnits.get(variant.getCode());
            String displayName = (label.isEmpty() ? product.getName() : product.getName() + " (" + label + ")")
                    + (baseUnit == null ? "" : " - giá cơ bản: " + SellableUnitController.SellableUnitView.of(baseUnit).label());
            return new ProductVariantView(variant.getCode(), shownLabel, product.getCode(), product.getName(),
                    values.stream().map(a -> new AttributeValueView(a.getAttribute().getCode(),
                            a.getAttribute().getName(), a.getValue())).toList(),
                    displayName, variant.getAdditionalDesc());
        }).toList();
    }

    static Specification<ProductVariant> matching(String productCode, String category) {
        return (root, query, cb) -> {
            // Fetched rather than only joined, so listing variants does not load each product separately.
            @SuppressWarnings("unchecked")
            Join<ProductVariant, Product> product =
                    (Join<ProductVariant, Product>) root.<ProductVariant, Product>fetch("product");
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(product.get("active")));
            if (productCode != null && !productCode.isBlank()) {
                where.add(cb.equal(cb.lower(product.get("code")), productCode.trim().toLowerCase(Locale.ROOT)));
            }
            if (category != null && !category.isBlank()) {
                where.add(cb.equal(cb.lower(product.get("category")), category.trim().toLowerCase(Locale.ROOT)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /**
     * @param label       what tells this variant apart, e.g. "M / Trắng", for showing it as a choice: the values of
     *                    the attributes that set the product's variants apart; the product name when it has none
     * @param attributes  every attribute value the variant has
     * @param displayName the product, what tells the variant apart and its base price, e.g.
     *                    "Vớ y khoa (M / Trắng) - giá cơ bản: Cái - 150.000 đ", for choosing among the variants of
     *                    several products; the price is left out when no unit of the variant has one
     * @param additionalDesc further details of the variant, such as what a size fits; null when there are none
     */
    public record ProductVariantView(String code, String label, String productCode, String productName,
                                     List<AttributeValueView> attributes, String displayName,
                                     String additionalDesc) {
    }

    public record AttributeValueView(String code, String name, String value) {
    }
}
