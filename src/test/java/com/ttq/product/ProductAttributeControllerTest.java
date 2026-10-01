package com.ttq.product;

import com.ttq.action.ActionContext;
import com.ttq.product.ProductAttributeController.ProductAttributeView;
import com.ttq.product.ProductAttributeController.ProductRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static com.ttq.product.CatalogFixture.BLOOD_PRESSURE_MONITOR;
import static com.ttq.product.CatalogFixture.HEARING_AID_WIRED;
import static com.ttq.product.CatalogFixture.HEARING_AID_WIRELESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
class ProductAttributeControllerTest {

    private static final String WIRELESS_NAME = "[Rionet] Máy trợ thính không dây - HB-23P";

    @Autowired
    ProductAttributeController controller;

    @Autowired
    ProductComparisonAction comparison;

    @Autowired
    BrandRepository brands;

    @Autowired
    ProductRepository products;

    @Autowired
    AttributeDefinitionRepository definitions;

    @Autowired
    ProductAttributeRepository productAttributes;

    @BeforeEach
    void loadCatalog() {
        CatalogFixture.load(brands, products);
        CatalogFixture.loadComparison(products, definitions, productAttributes);
    }

    @Test
    void listsWhatAProductsAttributesSayForComparingIt() {
        assertThat(controller.list(HEARING_AID_WIRED.toLowerCase()))
                .extracting(ProductAttributeView::code, ProductAttributeView::name, ProductAttributeView::validValue,
                        ProductAttributeView::comparison, ProductAttributeView::relatedProducts)
                .containsExactly(tuple("ATTR-17", "Kết nối", "Có dây",
                        "So với không dây: giá thấp, dễ dùng; kém thẩm mỹ.",
                        java.util.List.of(new ProductRef(HEARING_AID_WIRELESS, WIRELESS_NAME))));
        assertThat(controller.list("NO-SUCH")).isEmpty();
    }

    @Test
    void relatedProductsLeaveOutOnesThatAreNotInTheCatalog() {
        // PRODUCT-99999 is named as related to the monitor but does not exist.
        assertThat(controller.relatedProducts(BLOOD_PRESSURE_MONITOR)).extracting(ProductRef::code)
                .containsExactly(HEARING_AID_WIRELESS);
        assertThat(controller.relatedProducts("NO-SUCH")).isEmpty();
    }

    @Test
    void theComparisonIsWrittenFromTheCatalog() {
        String message = comparison.execute(context(HEARING_AID_WIRED)).message();

        assertThat(message).startsWith("So sánh [Rionet] Máy trợ thính có dây đeo - HA-20DX với các sản phẩm khác")
                .contains("• Kết nối (Có dây): So với không dây: giá thấp, dễ dùng; kém thẩm mỹ.")
                .contains("Sản phẩm khác: " + WIRELESS_NAME)
                .doesNotContain("Cách chọn");
        assertThat(comparison.execute(context(BLOOD_PRESSURE_MONITOR)).message())
                .contains("Cách chọn: Chọn loại nối dây nếu hay đo tại nhà.");
        assertThat(comparison.execute(context(CatalogFixture.EYE_MASK)).message())
                .startsWith("Hiện chưa có thông tin so sánh");
    }

    private static ActionContext context(String productCode) {
        return new ActionContext(UUID.randomUUID(), null, Map.of("productCode", productCode));
    }
}
