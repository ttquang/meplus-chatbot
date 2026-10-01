package com.ttq.product;

import com.ttq.product.BrandController.BrandView;
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
import java.util.List;
import java.util.Locale;

/**
 * The medical supply product catalog. The data is loaded into the database outside the application.
 * Only active products are returned; deactivating a row takes a product off the list without deleting it.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Sort CATALOG_ORDER = Sort.by("category", "name");

    private final ProductRepository products;
    private final ProductTagRepository productTags;

    public ProductController(ProductRepository products, ProductTagRepository productTags) {
        this.products = products;
        this.productTags = productTags;
    }

    /**
     * Every filter is optional and they combine. category, brandCode and brand (the
     * brand's name) match the whole value ignoring case; q matches part of the name, brand name or description.
     * tags are tag codes, repeated or comma separated: a product must carry one of those asked for
     * in each tag group, and a tag no listed product carries is ignored. limit keeps only the
     * first products of the list.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<ProductView> list(@RequestParam(required = false) String category,
                                  @RequestParam(required = false) String brandCode,
                                  @RequestParam(required = false) String brand,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false) List<String> tags,
                                  @RequestParam(required = false) Integer limit) {
        List<Product> found = products.findAll(matching(category, brandCode, brand, q), CATALOG_ORDER);
        if (tags != null && !tags.isEmpty()) {
            ProductTagIndex index = ProductTagIndex.of(found, productTags);
            ProductTagIndex.Selection selection = index.select(tags);
            found = found.stream().filter(p -> index.matches(p, selection)).toList();
        }
        if (limit != null && limit > 0 && found.size() > limit) {
            found = found.subList(0, limit);
        }
        return found.stream().map(ProductView::of).toList();
    }

    /** A product by code, ignoring case, e.g. DGN-001. */
    @GetMapping("/{code}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductView> get(@PathVariable String code) {
        return ResponseEntity.of(products.findByCodeIgnoreCaseAndActiveTrue(code.trim()).map(ProductView::of));
    }

    static Specification<Product> matching(String category, String brandCode, String brand, String q) {
        return (root, query, cb) -> {
            // Fetched rather than only joined, so listing products does not load each brand separately.
            @SuppressWarnings("unchecked")
            Join<Product, Brand> brandJoin = (Join<Product, Brand>) root.<Product, Brand>fetch("brand");
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("active")));
            if (hasText(category)) {
                where.add(cb.equal(cb.lower(root.get("category")), lower(category)));
            }
            if (hasText(brandCode)) {
                where.add(cb.equal(cb.lower(brandJoin.get("code")), lower(brandCode)));
            }
            if (hasText(brand)) {
                where.add(cb.equal(cb.lower(brandJoin.get("name")), lower(brand)));
            }
            if (hasText(q)) {
                // Escaped so a % or _ typed by the caller is matched literally.
                String pattern = "%" + lower(q).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(brandJoin.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String lower(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record ProductView(String code, String name, BrandView brand, String category, String description,
                              String uom) {

        static ProductView of(Product product) {
            return new ProductView(product.getCode(), product.getName(), BrandView.of(product.getBrand()),
                    product.getCategory(), product.getDescription(), product.getUom());
        }
    }
}
