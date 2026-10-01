package com.ttq.product;

import com.ttq.product.BrandController.BrandView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static com.ttq.product.CatalogFixture.KHAC;
import static com.ttq.product.CatalogFixture.OMRON;
import static com.ttq.product.CatalogFixture.RIONET;
import static com.ttq.product.CatalogFixture.THREE_M;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BrandControllerTest {

    @Autowired
    BrandController controller;

    @Autowired
    BrandRepository brands;

    @Autowired
    ProductRepository products;

    @BeforeEach
    void loadCatalog() {
        CatalogFixture.load(brands, products);
    }

    @Test
    void listsBrands() {
        assertThat(controller.list(null)).extracting(BrandView::code).contains(OMRON, RIONET, THREE_M, KHAC);
    }

    @Test
    void filtersByCountryIgnoringCase() {
        assertThat(controller.list("NHẬT BẢN")).extracting(BrandView::name)
                .containsExactlyInAnyOrder("Omron", "Rionet");
    }

    @Test
    void findsABrandByCodeOrName() {
        BrandView threeM = controller.getByName("3m™").getBody();
        assertThat(threeM).isEqualTo(new BrandView(THREE_M, "3M™", "Mỹ"));
        assertThat(controller.get(THREE_M.toLowerCase()).getBody()).isEqualTo(threeM);
    }

    @Test
    void aBrandMayHaveNoCountry() {
        assertThat(controller.get(KHAC).getBody()).isEqualTo(new BrandView(KHAC, "Khác", null));
    }

    @Test
    void anUnknownBrandIsNotFound() {
        assertThat(controller.getByName("No Such Brand").getStatusCode().value()).isEqualTo(404);
        assertThat(controller.get("NO-SUCH").getStatusCode().value()).isEqualTo(404);
    }
}
