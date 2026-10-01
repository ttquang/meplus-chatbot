package com.ttq.product;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    List<Brand> findByCountryIgnoreCase(String country, Sort sort);

    Optional<Brand> findByCodeIgnoreCase(String code);

    Optional<Brand> findByNameIgnoreCase(String name);
}
