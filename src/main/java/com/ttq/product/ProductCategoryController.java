package com.ttq.product;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/** The kinds of product customers can ask for. */
@RestController
@RequestMapping("/api/product-categories")
public class ProductCategoryController {

    private static final Sort BY_NAME = Sort.by("name");

    private final ProductCategoryRepository categories;

    public ProductCategoryController(ProductCategoryRepository categories) {
        this.categories = categories;
    }

    /** @param q words to look for in the name or description, ignoring case; all categories when omitted */
    @GetMapping
    @Transactional(readOnly = true)
    public List<ProductCategoryView> list(@RequestParam(required = false) String q) {
        String needle = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        return categories.findByActiveTrue(BY_NAME).stream()
                .filter(c -> needle.isEmpty()
                        || c.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || (c.getDescription() != null && c.getDescription().toLowerCase(Locale.ROOT).contains(needle)))
                .map(ProductCategoryView::of)
                .toList();
    }

    /** A category by code, ignoring case, e.g. CATEGORY-002. */
    @GetMapping("/{code}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductCategoryView> get(@PathVariable String code) {
        return ResponseEntity.of(categories.findByCodeIgnoreCaseAndActiveTrue(code.trim())
                .map(ProductCategoryView::of));
    }

    public record ProductCategoryView(String code, String name, String description) {

        public static ProductCategoryView of(ProductCategory category) {
            return new ProductCategoryView(category.getCode(), category.getName(), category.getDescription());
        }
    }
}
