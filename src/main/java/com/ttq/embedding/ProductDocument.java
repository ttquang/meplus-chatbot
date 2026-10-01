package com.ttq.embedding;

import com.ttq.product.Product;
import com.ttq.product.ProductVariant;
import com.ttq.product.ProductVariantAttribute;
import com.ttq.product.SellableUnit;
import com.ttq.product.Tag;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * The text a product is embedded from: everything a customer might describe it by, in the
 * catalog's own language. Empty fields are left out so they add no noise to the vector.
 */
final class ProductDocument {

    private static final Locale VIETNAMESE = Locale.of("vi", "VN");

    private ProductDocument() {
    }

    static String of(Product product, List<Tag> tags, List<ProductVariant> variants,
                    Map<String, List<ProductVariantAttribute>> attributes, List<SellableUnit> units) {
        StringBuilder text = new StringBuilder();
        line(text, "Sản phẩm", product.getName() + " (" + product.getCode() + ")");
        line(text, "Thương hiệu", brand(product));
        line(text, "Danh mục", product.getCategory());
        line(text, "Đơn vị tính", product.getUom());
        line(text, "Mô tả", product.getDescription());

        // Tags by group, so "Độ trong: Đục, Trong suốt" reads as the question and its answers.
        Map<String, StringJoiner> groups = new TreeMap<>();
        for (Tag tag : tags) {
            groups.computeIfAbsent(tag.getGroup(), g -> new StringJoiner(", ")).add(tag.getName());
        }
        groups.forEach((group, names) -> line(text, group, names.toString()));

        for (ProductVariant variant : variants) {
            StringJoiner parts = new StringJoiner("; ");
            for (ProductVariantAttribute a : attributes.getOrDefault(variant.getCode(), List.of())) {
                add(parts, a.getAttribute().getName().toLowerCase(VIETNAMESE), a.getValue());
            }
            List<String> sold = units.stream()
                    .filter(u -> u.getProductVariant().getCode().equals(variant.getCode()))
                    .map(ProductDocument::sale)
                    .toList();
            if (!sold.isEmpty()) {
                parts.add("bán theo " + String.join(", ", sold));
            }
            line(text, "Phiên bản " + variant.getCode(), parts.length() == 0 ? null : parts.toString());
        }
        return text.toString().strip();
    }

    private static String brand(Product product) {
        String country = product.getBrand().getCountry();
        return product.getBrand().getName() + (isBlank(country) ? "" : " (" + country + ")");
    }

    private static String sale(SellableUnit unit) {
        String text = unit.getSaleUom() + (unit.getQuantity() > 1 ? " " + unit.getQuantity() : "");
        BigDecimal price = unit.getPrice();
        return price == null ? text : text + " giá " + NumberFormat.getIntegerInstance(VIETNAMESE).format(price) + "đ";
    }

    private static void add(StringJoiner parts, String label, String value) {
        if (!isBlank(value)) {
            parts.add(label == null ? value.strip() : label + " " + value.strip());
        }
    }

    private static void line(StringBuilder text, String label, String value) {
        if (!isBlank(value)) {
            text.append(label).append(": ").append(value.strip()).append('\n');
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
