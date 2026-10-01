package com.ttq.product;

import com.ttq.product.ProductController.ProductView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static com.ttq.product.CatalogFixture.BLOOD_PRESSURE_MONITOR;
import static com.ttq.product.CatalogFixture.EYE_MASK;
import static com.ttq.product.CatalogFixture.FILM_DRESSING;
import static com.ttq.product.CatalogFixture.HEARING_AID_WIRED;
import static com.ttq.product.CatalogFixture.HEARING_AID_WIRELESS;
import static com.ttq.product.CatalogFixture.OMRON;
import static com.ttq.product.CatalogFixture.RIONET;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    ProductController controller;

    @Autowired
    ProductRepository products;

    @Autowired
    BrandRepository brands;

    @BeforeEach
    void loadCatalog() {
        CatalogFixture.load(brands, products);
    }

    @Test
    void listsProductsWithTheirBrands() {
        assertThat(controller.list(null, null, null, null, null, null))
                .extracting(ProductView::code)
                .contains(HEARING_AID_WIRED, HEARING_AID_WIRELESS, BLOOD_PRESSURE_MONITOR, FILM_DRESSING, EYE_MASK);
        assertThat(controller.list(null, null, null, null, null, null)).allSatisfy(p -> {
            assertThat(p.brand().code()).isNotBlank();
            assertThat(p.brand().name()).isNotBlank();
        });
    }

    @Test
    void filtersCombineAndIgnoreCase() {
        assertThat(controller.list("MÁY TRỢ THÍNH", null, null, null, null, null))
                .extracting(ProductView::code)
                .containsExactlyInAnyOrder(HEARING_AID_WIRED, HEARING_AID_WIRELESS);
        assertThat(controller.list("máy trợ thính", RIONET.toLowerCase(), null, null, null, null))
                .extracting(ProductView::code)
                .containsExactlyInAnyOrder(HEARING_AID_WIRED, HEARING_AID_WIRELESS);
        assertThat(controller.list(null, null, "omron", null, null, null)).extracting(ProductView::code)
                .containsExactly(BLOOD_PRESSURE_MONITOR);
    }

    @Test
    void searchMatchesPartOfNameBrandOrDescription() {
        assertThat(controller.list(null, null, null, "trợ thính", null, null)).extracting(ProductView::code)
                .containsExactlyInAnyOrder(HEARING_AID_WIRED, HEARING_AID_WIRELESS);
        assertThat(controller.list(null, null, null, "hem-7156", null, null)).extracting(ProductView::code)
                .containsExactly(BLOOD_PRESSURE_MONITOR);
        assertThat(controller.list(null, null, null, "3m™", null, null)).extracting(ProductView::code)
                .containsExactly(FILM_DRESSING);
    }

    @Test
    void findsAProductByCodeWithItsBrandAndUnitOfMeasure() {
        ProductView product = controller.get(BLOOD_PRESSURE_MONITOR.toLowerCase()).getBody();

        assertThat(product).isNotNull();
        assertThat(product.name()).isEqualTo("[Omron] Máy đo huyết áp bắp tay tự động - HEM-7156");
        assertThat(product.category()).isEqualTo("Máy đo huyết áp");
        assertThat(product.uom()).isEqualTo("cái");
        assertThat(product.brand()).isEqualTo(new BrandController.BrandView(OMRON, "Omron", "Nhật Bản"));
        assertThat(controller.get("NO-SUCH").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void anInactiveProductIsNeitherListedNorFound() {
        Brand acme = brands.save(new Brand("ACME", "Acme", "Mỹ"));
        Product retired = products.save(
                new Product("ORT-001", "Retired Splint", acme, "Orthopedics", "Old stock", "cái"));
        retired.setActive(false);
        products.save(retired);

        assertThat(controller.list("Orthopedics", null, null, null, null, null)).isEmpty();
        assertThat(controller.get("ORT-001").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void limitKeepsOnlyTheFirstProducts() {
        assertThat(controller.list(null, null, null, null, null, 2)).hasSize(2)
                .extracting(ProductView::code)
                .containsExactlyElementsOf(controller.list(null, null, null, null, null, null).stream()
                        .map(ProductView::code).limit(2).toList());
    }
}
