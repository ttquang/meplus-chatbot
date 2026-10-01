package com.ttq.product;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The attribute values carried by a set of variants, for narrowing that set down by the values a
 * customer asked for. Values of one attribute are alternatives, so a variant matches an attribute
 * when it has any of the values asked for, and must match every attribute asked about.
 *
 * <p>A value asked for that no variant in the set carries is ignored rather than matching nothing,
 * so one left over from an earlier category does not empty the list.
 */
final class VariantAttributeIndex {

    /**
     * One value an attribute takes among the variants.
     *
     * @param defining true when it tells the variants of a product apart, so it is worth asking about first
     */
    record Value(String attributeCode, String attributeName, String value, boolean defining) {

        /** What a customer's pick is stored as, e.g. {@code ATTR-27:M}. */
        String code() {
            return attributeCode + ":" + value;
        }
    }

    private final Map<String, Set<String>> valuesByVariant = new HashMap<>();
    private final Map<String, Product> productOfVariant = new HashMap<>();
    /** How to choose between an attribute's values, by product code and lower-cased attribute code. */
    private final Map<String, String> guidance = new HashMap<>();
    /** Values carried by at least one variant, by lower-cased code, attributes that tell variants apart first. */
    private final Map<String, Value> values = new LinkedHashMap<>();

    private VariantAttributeIndex(Collection<ProductVariant> variants, List<ProductVariantAttribute> attributes,
                                  Set<String> definingKeys, List<ProductAttribute> productAttributes) {
        variants.forEach(v -> {
            valuesByVariant.put(v.getCode(), new HashSet<>());
            productOfVariant.put(v.getCode(), v.getProduct());
        });
        productAttributes.stream()
                .filter(pa -> pa.getGuidance() != null && !pa.getGuidance().isBlank())
                .forEach(pa -> guidance.put(guidanceKey(pa.getProduct().getCode(), pa.getAttribute().getCode()),
                        pa.getGuidance().strip()));
        Map<String, Value> carried = new LinkedHashMap<>();
        for (ProductVariantAttribute attribute : attributes) {
            String variantCode = attribute.getVariant().getCode();
            Set<String> ofVariant = valuesByVariant.get(variantCode);
            if (ofVariant == null || attribute.getValue() == null || attribute.getValue().isBlank()) {
                continue;
            }
            AttributeDefinition definition = attribute.getAttribute();
            Value value = new Value(definition.getCode(), definition.getName(), attribute.getValue().strip(),
                    definingKeys.contains(productOfVariant.get(variantCode).getCode() + "/" + definition.getCode()));
            String key = key(value.code());
            ofVariant.add(key);
            carried.merge(key, value, (seen, again) -> seen.defining() ? seen : again);
        }
        carried.entrySet().stream()
                .sorted(Map.Entry.<String, Value>comparingByValue(
                        Comparator.comparing((Value v) -> !v.defining()).thenComparing(Value::attributeCode)))
                .forEach(e -> values.put(e.getKey(), e.getValue()));
    }

    static VariantAttributeIndex of(List<ProductVariant> variants, ProductVariantAttributeRepository variantAttributes,
                                    ProductAttributeRepository productAttributes) {
        if (variants.isEmpty()) {
            return new VariantAttributeIndex(List.of(), List.of(), Set.of(), List.of());
        }
        List<String> variantCodes = variants.stream().map(ProductVariant::getCode).toList();
        List<String> productCodes = variants.stream().map(v -> v.getProduct().getCode()).distinct().toList();
        Set<String> defining = new HashSet<>();
        List<ProductAttribute> ofProducts = productAttributes.findByProductCodeIn(productCodes);
        for (ProductAttribute pa : ofProducts) {
            if (pa.isVariantDefining()) {
                defining.add(pa.getProduct().getCode() + "/" + pa.getAttribute().getCode());
            }
        }
        return new VariantAttributeIndex(variants,
                variantAttributes.findByVariantCodeInOrderByAttributeCodeAsc(variantCodes), defining, ofProducts);
    }

    /** The values carried by at least one of the variants. */
    Collection<Value> values() {
        return values.values();
    }

    /**
     * The values asked for, by attribute, keeping only those some variant carries. Codes are matched
     * ignoring case; an empty or null list asks for nothing.
     */
    Selection select(Collection<String> requested) {
        Map<String, Set<String>> byAttribute = new TreeMap<>();
        if (requested != null) {
            requested.stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(code -> values.get(key(code)))
                    .filter(value -> value != null)
                    .forEach(value -> byAttribute.computeIfAbsent(key(value.attributeCode()), a -> new HashSet<>())
                            .add(key(value.code())));
        }
        return new Selection(byAttribute);
    }

    /** True when the variant has a value of every attribute asked about. */
    boolean matches(ProductVariant variant, Selection selection) {
        return matches(variant.getCode(), selection, null);
    }

    /**
     * How many variants would match if {@code value} were the only one asked for its attribute, the
     * other attributes staying as asked. This is what picking it would leave, so a value of an
     * attribute already answered still counts the variants it would switch to.
     */
    long count(Value value, Selection selection) {
        String code = key(value.code());
        String attribute = key(value.attributeCode());
        return valuesByVariant.keySet().stream()
                .filter(variant -> valuesByVariant.get(variant).contains(code))
                .filter(variant -> matches(variant, selection, attribute))
                .count();
    }

    /**
     * How to choose between the values of the value's attribute, for the products that still have
     * one of them given what is asked for on the other attributes; null when none says. The products'
     * guidance usually differs, such as the measurements a size fits, so it is named by product
     * unless every such product gives the same.
     */
    String guidance(Value value, Selection selection) {
        String attribute = key(value.attributeCode());
        Map<String, String> byProduct = new java.util.TreeMap<>();
        valuesByVariant.forEach((variantCode, carried) -> {
            Product product = productOfVariant.get(variantCode);
            String text = guidance.get(guidanceKey(product.getCode(), value.attributeCode()));
            if (text != null && carried.stream().anyMatch(v -> v.startsWith(attribute + ":"))
                    && matches(variantCode, selection, attribute)) {
                byProduct.put(product.getName(), text);
            }
        });
        if (byProduct.isEmpty()) {
            return null;
        }
        if (new HashSet<>(byProduct.values()).size() == 1) {
            return byProduct.values().iterator().next();
        }
        return byProduct.entrySet().stream().limit(3).map(e -> e.getKey() + ": " + e.getValue())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String guidanceKey(String productCode, String attributeCode) {
        return productCode + "/" + key(attributeCode);
    }

    private boolean matches(String variantCode, Selection selection, String skipAttribute) {
        Set<String> carried = valuesByVariant.getOrDefault(variantCode, Set.of());
        return selection.byAttribute().entrySet().stream()
                .filter(e -> !e.getKey().equals(skipAttribute))
                .allMatch(e -> e.getValue().stream().anyMatch(carried::contains));
    }

    private static String key(String code) {
        return code.strip().toLowerCase(Locale.ROOT);
    }

    /** @param byAttribute lower-cased codes of the values asked for, by lower-cased attribute code */
    record Selection(Map<String, Set<String>> byAttribute) {

        boolean isEmpty() {
            return byAttribute.isEmpty();
        }
    }
}
