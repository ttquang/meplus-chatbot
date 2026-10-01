package com.ttq.embedding;

import com.ttq.product.Product;
import com.ttq.product.ProductRepository;
import com.ttq.product.ProductTag;
import com.ttq.product.ProductTagRepository;
import com.ttq.product.ProductVariant;
import com.ttq.product.ProductVariantAttribute;
import com.ttq.product.ProductVariantAttributeRepository;
import com.ttq.product.ProductVariantRepository;
import com.ttq.product.SellableUnit;
import com.ttq.product.SellableUnitRepository;
import com.ttq.product.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps one embedding per active product, made from all of its details. A product whose details
 * and model are unchanged since it was last embedded is skipped, so running this again after a
 * partial failure, or on every start, only does the work that is missing.
 *
 * <p>Deliberately not transactional: a transaction held open across calls to the embedding server
 * would pin a database connection for as long as the model takes. Each repository call commits on
 * its own, and the associations are loaded up front by the repositories' entity graphs.
 */
@Service
public class ProductEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ProductEmbeddingService.class);

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductVariantAttributeRepository variantAttributes;
    private final SellableUnitRepository units;
    private final ProductTagRepository productTags;
    private final ProductEmbeddingRepository embeddings;
    private final EmbeddingClient client;
    private final EmbeddingProperties properties;
    private final ReentrantLock running = new ReentrantLock();

    public ProductEmbeddingService(ProductRepository products, ProductVariantRepository variants,
                                   ProductVariantAttributeRepository variantAttributes, SellableUnitRepository units, ProductTagRepository productTags,
                                   ProductEmbeddingRepository embeddings, EmbeddingClient client,
                                   EmbeddingProperties properties) {
        this.products = products;
        this.variants = variants;
        this.variantAttributes = variantAttributes;
        this.units = units;
        this.productTags = productTags;
        this.embeddings = embeddings;
        this.client = client;
        this.properties = properties;
    }

    /**
     * @param total    active products looked at
     * @param embedded products embedded now, new or changed
     * @param skipped  products already embedded from the same details and model
     */
    public record Result(int total, int embedded, int skipped) {
    }

    /**
     * Embeds every active product that is new or changed. Stops at the first failure; what was
     * embedded before it is kept.
     *
     * @throws ReindexInProgressException if another run is in progress
     * @throws EmbeddingException         if the embedding server fails
     */
    public Result reindexAll() {
        if (!running.tryLock()) {
            throw new ReindexInProgressException();
        }
        try {
            int total = 0;
            int embedded = 0;
            int batchSize = Math.max(1, properties.batchSize());
            // Ordered by id so pages do not shift while the run is going.
            for (int page = 0; ; page++) {
                Page<Product> batch = products.findByActiveTrue(PageRequest.of(page, batchSize, Sort.by("id")));
                embedded += embedBatch(batch.getContent());
                total += batch.getNumberOfElements();
                log.info("Product embeddings: {}/{} products done, {} embedded",
                        total, batch.getTotalElements(), embedded);
                if (!batch.hasNext()) {
                    return new Result(total, embedded, total - embedded);
                }
            }
        } finally {
            running.unlock();
        }
    }

    private int embedBatch(List<Product> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        List<String> codes = batch.stream().map(Product::getCode).toList();

        Map<String, List<Tag>> tagsByProduct = new HashMap<>();
        for (ProductTag pt : productTags.findByProductCodeIn(codes)) {
            tagsByProduct.computeIfAbsent(pt.getProduct().getCode(), c -> new ArrayList<>()).add(pt.getTag());
        }
        Map<String, List<ProductVariant>> variantsByProduct = new HashMap<>();
        for (ProductVariant v : variants.findByProductCodeInOrderByCodeAsc(codes)) {
            variantsByProduct.computeIfAbsent(v.getProduct().getCode(), c -> new ArrayList<>()).add(v);
        }
        Map<String, List<ProductVariantAttribute>> attributesByVariant = new HashMap<>();
        for (ProductVariantAttribute a : variantAttributes.findByVariantProductCodeInOrderByAttributeCodeAsc(codes)) {
            attributesByVariant.computeIfAbsent(a.getVariant().getCode(), c -> new ArrayList<>()).add(a);
        }
        Map<String, List<SellableUnit>> unitsByProduct = new HashMap<>();
        for (SellableUnit u : units.findByProductVariantProductCodeInOrderByCodeAsc(codes)) {
            unitsByProduct.computeIfAbsent(u.getProductVariant().getProduct().getCode(), c -> new ArrayList<>())
                    .add(u);
        }
        Map<String, ProductEmbedding> existing = new HashMap<>();
        embeddings.findByProductCodeIn(codes).forEach(e -> existing.put(e.getProductCode(), e));

        List<Product> stale = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        for (Product product : batch) {
            String code = product.getCode();
            String content = ProductDocument.of(product,
                    tagsByProduct.getOrDefault(code, List.of()),
                    variantsByProduct.getOrDefault(code, List.of()),
                    attributesByVariant,
                    unitsByProduct.getOrDefault(code, List.of()));
            ProductEmbedding current = existing.get(code);
            if (current == null || !current.getContentHash().equals(EmbeddingSupport.hash(properties.model(), content))) {
                stale.add(product);
                contents.add(content);
            }
        }
        if (stale.isEmpty()) {
            return 0;
        }

        List<float[]> vectors = client.embed(contents);
        List<ProductEmbedding> toSave = new ArrayList<>();
        for (int i = 0; i < stale.size(); i++) {
            String code = stale.get(i).getCode();
            ProductEmbedding row = existing.getOrDefault(code, new ProductEmbedding(code));
            row.update(contents.get(i), EmbeddingSupport.hash(properties.model(), contents.get(i)), properties.model(), vectors.get(i));
            toSave.add(row);
        }
        embeddings.saveAll(toSave);
        return toSave.size();
    }
}
