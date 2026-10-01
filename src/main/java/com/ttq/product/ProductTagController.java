package com.ttq.product;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The tags customers can narrow a product search by. Only tags that some product of the search
 * carries are listed, each with how many products picking it would leave, so a question about a
 * group can offer just the answers that lead somewhere.
 */
@RestController
@RequestMapping("/api/product-tags")
public class ProductTagController {

    private final ProductRepository products;
    private final ProductTagRepository productTags;

    public ProductTagController(ProductRepository products, ProductTagRepository productTags) {
        this.products = products;
        this.productTags = productTags;
    }

    /**
     * The filters are those of {@code /api/products}, all optional; without any, the tags of the
     * whole catalog are listed. tags are the tag codes already picked, which the counts take into account.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<ProductTagView> list(@RequestParam(required = false) String category,
                                     @RequestParam(required = false) String brandCode,
                                     @RequestParam(required = false) String brand,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(required = false) List<String> tags) {
        List<Product> found = products.findAll(
                ProductController.matching(category, brandCode, brand, q), Sort.unsorted());
        ProductTagIndex index = ProductTagIndex.of(found, productTags);
        ProductTagIndex.Selection selection = index.select(tags);
        return index.tags().stream()
                .map(tag -> ProductTagView.of(tag, index.count(tag, selection)))
                .toList();
    }

    /**
     * @param count how many of the searched products would be left if this tag were picked, the
     *              other groups staying as they are; 0 when picking it would leave none
     */
    public record ProductTagView(String code, String name, String group, String synonyms, long count) {

        static ProductTagView of(Tag tag, long count) {
            return new ProductTagView(tag.getCode(), tag.getName(), tag.getGroup(), tag.getSynonyms(), count);
        }
    }
}
