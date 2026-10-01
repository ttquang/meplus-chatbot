package com.ttq.product;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The attributes of a product with what a customer comparing it with other products needs to know:
 * the value it has, how to choose between values, how it compares and which products of its
 * category have another value. Only active products are covered.
 */
@RestController
@RequestMapping("/api/product-attributes")
public class ProductAttributeController {

    private final ProductAttributeRepository productAttributes;
    private final ProductRepository products;

    public ProductAttributeController(ProductAttributeRepository productAttributes, ProductRepository products) {
        this.productAttributes = productAttributes;
        this.products = products;
    }

    /** @param productCode e.g. PRODUCT-00004, ignoring case; nothing is listed for an unknown or inactive product */
    @GetMapping
    @Transactional(readOnly = true)
    public List<ProductAttributeView> list(@RequestParam String productCode) {
        List<ProductAttribute> found = attributesOf(productCode);
        Map<String, Product> related = productsByCode(found);
        return found.stream().map(pa -> new ProductAttributeView(pa.getAttribute().getCode(),
                pa.getAttribute().getName(), pa.isVariantDefining(), pa.getValidValue(), pa.getGuidance(),
                pa.getComparison(), refs(codes(pa), related))).toList();
    }

    /** The active products of the same category that have another value for one of the product's attributes. */
    @GetMapping("/related-products")
    @Transactional(readOnly = true)
    public List<ProductRef> relatedProducts(@RequestParam String productCode) {
        List<ProductAttribute> found = attributesOf(productCode);
        Set<String> codes = new LinkedHashSet<>();
        found.forEach(pa -> codes.addAll(codes(pa)));
        return refs(List.copyOf(codes), productsByCode(found));
    }

    private List<ProductAttribute> attributesOf(String productCode) {
        return productAttributes.findByProductCodeIgnoreCaseAndProductActiveTrueOrderByAttributeCodeAsc(
                productCode.trim());
    }

    private Map<String, Product> productsByCode(List<ProductAttribute> found) {
        Set<String> codes = found.stream().flatMap(pa -> codes(pa).stream()).collect(Collectors.toSet());
        return codes.isEmpty() ? Map.of() : products.findByCodeInAndActiveTrue(codes).stream()
                .collect(Collectors.toMap(Product::getCode, Function.identity()));
    }

    /** The products among {@code codes} that are active, in the order the codes are given. */
    private static List<ProductRef> refs(List<String> codes, Map<String, Product> byCode) {
        return codes.stream().map(byCode::get).filter(p -> p != null)
                .map(p -> new ProductRef(p.getCode(), p.getName())).toList();
    }

    private static List<String> codes(ProductAttribute attribute) {
        String codes = attribute.getRelatedProductCodes();
        return codes == null ? List.of() : Arrays.stream(codes.split("[;,]"))
                .map(String::strip).filter(c -> !c.isEmpty()).sorted(Comparator.naturalOrder()).toList();
    }

    /**
     * @param validValue      the value the product has, e.g. "Có dây"
     * @param guidance        how to choose between the values; null when there is none to give
     * @param comparison      how the product compares with others on this attribute; null when unknown
     * @param relatedProducts active products of the same category with another value
     */
    public record ProductAttributeView(String code, String name, boolean variantDefining, String validValue,
                                       String guidance, String comparison, List<ProductRef> relatedProducts) {
    }

    public record ProductRef(String code, String name) {
    }
}
