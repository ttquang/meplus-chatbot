package com.ttq.embedding;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Finds products by what a customer wrote, by meaning rather than by matching words. */
@RestController
@RequestMapping("/api/product-search")
public class ProductSearchController {

    private final ProductSearchService service;

    public ProductSearchController(ProductSearchService service) {
        this.service = service;
    }

    /**
     * @param message  what the customer wrote, e.g. "máy đo huyết áp cho người già"
     * @param limit    most matches to return; 5 when omitted
     * @param minScore matches scoring below this are left out; none are when omitted
     */
    public record SearchRequest(@NotBlank @Size(max = 2000) String message,
                                @Min(1) @Max(50) Integer limit,
                                @DecimalMin("-1") @DecimalMax("1") Double minScore) {
    }

    public record MatchView(String code, String name, String brand, String category,
                            String description, String uom, double score) {
    }

    @PostMapping
    public List<MatchView> search(@Valid @RequestBody SearchRequest request) {
        return service.search(request.message(),
                        request.limit() == null ? 5 : request.limit(),
                        request.minScore() == null ? -1 : request.minScore())
                .stream()
                .map(m -> new MatchView(m.product().getCode(), m.product().getName(),
                        m.product().getBrand().getName(), m.product().getCategory(),
                        m.product().getDescription(), m.product().getUom(),
                        m.score()))
                .toList();
    }
}
