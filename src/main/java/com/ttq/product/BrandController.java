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

/** The brands of the medical supply catalog. The data is loaded into the database outside the application. */
@RestController
@RequestMapping("/api/brands")
public class BrandController {

    private static final Sort BY_NAME = Sort.by("name");

    private final BrandRepository brands;

    public BrandController(BrandRepository brands) {
        this.brands = brands;
    }

    /** @param country e.g. Japan, ignoring case; every brand is returned when it is omitted */
    @GetMapping
    @Transactional(readOnly = true)
    public List<BrandView> list(@RequestParam(required = false) String country) {
        List<Brand> rows = country == null || country.isBlank()
                ? brands.findAll(BY_NAME)
                : brands.findByCountryIgnoreCase(country.trim(), BY_NAME);
        return rows.stream().map(BrandView::of).toList();
    }

    /** A brand by code, ignoring case, e.g. OMRON. */
    @GetMapping("/{code}")
    @Transactional(readOnly = true)
    public ResponseEntity<BrandView> get(@PathVariable String code) {
        return ResponseEntity.of(brands.findByCodeIgnoreCase(code.trim()).map(BrandView::of));
    }

    /** A brand by name, ignoring case. */
    @GetMapping("/by-name/{name}")
    @Transactional(readOnly = true)
    public ResponseEntity<BrandView> getByName(@PathVariable String name) {
        return ResponseEntity.of(brands.findByNameIgnoreCase(name.trim()).map(BrandView::of));
    }

    public record BrandView(String code, String name, String country) {

        static BrandView of(Brand brand) {
            return new BrandView(brand.getCode(), brand.getName(), brand.getCountry());
        }
    }
}
