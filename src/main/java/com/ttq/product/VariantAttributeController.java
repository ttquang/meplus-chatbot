package com.ttq.product;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The attribute values customers can narrow the variants of a category by, such as the size "M".
 * Only values some matching variant has are listed, each with how many variants picking it would
 * leave, so a question about an attribute can offer just the answers that lead somewhere.
 */
@RestController
@RequestMapping("/api/variant-attributes")
public class VariantAttributeController {

    private final ProductVariantRepository variants;
    private final ProductVariantAttributeRepository variantAttributes;
    private final ProductAttributeRepository productAttributes;

    public VariantAttributeController(ProductVariantRepository variants,
                                      ProductVariantAttributeRepository variantAttributes,
                                      ProductAttributeRepository productAttributes) {
        this.variants = variants;
        this.variantAttributes = variantAttributes;
        this.productAttributes = productAttributes;
    }

    /**
     * The variants considered are those of {@code /api/product-variants}, filtered the same way and
     * all optional; without any, the values of every variant are listed. attributes are the value
     * codes already picked, which the counts take into account.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<VariantAttributeView> list(@RequestParam(required = false) String productCode,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(required = false) List<String> attributes) {
        List<ProductVariant> found = variants.findAll(
                ProductVariantController.matching(productCode, category), Sort.by("code"));
        VariantAttributeIndex index = VariantAttributeIndex.of(found, variantAttributes, productAttributes);
        VariantAttributeIndex.Selection selection = index.select(attributes);
        return index.values().stream()
                .map(value -> new VariantAttributeView(value.code(), value.value(), value.attributeName(),
                        index.count(value, selection), index.guidance(value, selection)))
                .toList();
    }

    /**
     * @param code  the value as it is picked, e.g. ATTR-27:M
     * @param name  the value as customers read it, e.g. M
     * @param group the attribute it is a value of, e.g. Kích cỡ
     * @param count how many of the variants would be left if this value were picked, the other
     *              attributes staying as they are; 0 when picking it would leave none
     * @param guidance how to choose between the values of the attribute, such as which size fits which
     *                 measurement, for the products that are left; null when none says
     */
    public record VariantAttributeView(String code, String name, String group, long count, String guidance) {
    }
}
