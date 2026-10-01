package com.ttq.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads the product categories from {@code data/product-categories.tsv}, one "name, tab,
 * description" per line, taken from the "Product Category" sheet of Merinco-Product v1.xlsx.
 *
 * <p>Runs at every start and only adds what is missing or changed, matching on name, so editing the
 * file is how the list changes. A category taken out of the file is left alone rather than deleted,
 * since it may still be embedded or referred to.
 */
@Order(1)
@Component
class ProductCategoryImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductCategoryImporter.class);
    private static final String FILE = "data/product-categories.tsv";

    private final ProductCategoryRepository categories;

    ProductCategoryImporter(ProductCategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, ProductCategory> byName = new HashMap<>();
        categories.findAll().forEach(c -> byName.put(c.getName(), c));
        int next = byName.size() + 1;
        int added = 0;
        int updated = 0;

        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new ClassPathResource(FILE).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = line.split("\t", 2);
                String name = cells[0].strip();
                String description = cells.length > 1 && !cells[1].isBlank() ? cells[1].strip() : null;
                ProductCategory existing = byName.get(name);
                if (existing == null) {
                    ProductCategory created = new ProductCategory(String.format("CATEGORY-%03d", next++), name, description);
                    byName.put(name, categories.save(created));
                    added++;
                } else if (!Objects.equals(existing.getDescription(), description)) {
                    existing.setDescription(description);
                    updated++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + FILE, e);
        }
        if (added > 0 || updated > 0) {
            log.info("Product categories: {} added, {} updated", added, updated);
        }
    }
}
