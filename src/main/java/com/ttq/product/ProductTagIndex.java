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
 * The tags carried by a set of products, for narrowing that set down by the tags a customer asked
 * for. Tags of one group are alternatives, so a product matches a group when it carries any of the
 * tags asked for in it, and must match every group asked about.
 *
 * <p>A tag asked for that no product in the set carries is ignored rather than matching nothing, so
 * a tag left over from an earlier search, or one that does not apply to this kind of product, does
 * not empty the list.
 */
final class ProductTagIndex {

    private final Map<String, Set<String>> tagsByProduct = new HashMap<>();
    /** Tags carried by at least one product, by lower-cased code, in the order they are listed. */
    private final Map<String, Tag> tags;

    private ProductTagIndex(Collection<Product> products, List<ProductTag> productTags) {
        products.forEach(p -> tagsByProduct.put(p.getCode(), new HashSet<>()));
        Map<String, Tag> carried = new HashMap<>();
        for (ProductTag productTag : productTags) {
            Set<String> tagsOfProduct = tagsByProduct.get(productTag.getProduct().getCode());
            if (tagsOfProduct != null) {
                Tag tag = productTag.getTag();
                tagsOfProduct.add(key(tag.getCode()));
                carried.put(key(tag.getCode()), tag);
            }
        }
        tags = new LinkedHashMap<>();
        carried.values().stream()
                .sorted(Comparator.comparingInt(Tag::getSortOrder).thenComparing(Tag::getCode))
                .forEach(t -> tags.put(key(t.getCode()), t));
    }

    static ProductTagIndex of(Collection<Product> products, ProductTagRepository productTags) {
        List<String> codes = products.stream().map(Product::getCode).toList();
        return new ProductTagIndex(products, codes.isEmpty() ? List.of() : productTags.findByProductCodeIn(codes));
    }

    /** The tags carried by at least one of the products, in the order they are listed. */
    Collection<Tag> tags() {
        return tags.values();
    }

    /**
     * The tags asked for, by group, keeping only those some product carries. Codes are matched
     * ignoring case; an empty or null list asks for nothing.
     */
    Selection select(Collection<String> requested) {
        Map<String, Set<String>> byGroup = new TreeMap<>();
        if (requested != null) {
            requested.stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(code -> tags.get(key(code)))
                    .filter(tag -> tag != null)
                    .forEach(tag -> byGroup.computeIfAbsent(tag.getGroup(), g -> new HashSet<>())
                            .add(key(tag.getCode())));
        }
        return new Selection(byGroup);
    }

    /** True when the product carries a tag of every group asked about. */
    boolean matches(Product product, Selection selection) {
        return matches(product.getCode(), selection, null);
    }

    /**
     * How many products would match if {@code tag} were the only tag asked for in its group, the
     * other groups staying as asked. This is what picking the tag would leave, so a tag of a group
     * already answered still counts the products it would switch to.
     */
    long count(Tag tag, Selection selection) {
        String code = key(tag.getCode());
        return tagsByProduct.keySet().stream()
                .filter(p -> tagsByProduct.get(p).contains(code))
                .filter(p -> matches(p, selection, tag.getGroup()))
                .count();
    }

    private boolean matches(String productCode, Selection selection, String skipGroup) {
        Set<String> carried = tagsByProduct.getOrDefault(productCode, Set.of());
        return selection.byGroup().entrySet().stream()
                .filter(e -> !e.getKey().equals(skipGroup))
                .allMatch(e -> e.getValue().stream().anyMatch(carried::contains));
    }

    private static String key(String code) {
        return code.strip().toLowerCase(Locale.ROOT);
    }

    /** @param byGroup lower-cased codes of the tags asked for, by group */
    record Selection(Map<String, Set<String>> byGroup) {

        boolean isEmpty() {
            return byGroup.isEmpty();
        }
    }
}
