package com.ttq.product;

import com.ttq.product.ProductController.ProductView;
import com.ttq.product.ProductTagController.ProductTagView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ttq.product.CatalogFixture.HEARING_AID_WIRED;
import static com.ttq.product.CatalogFixture.HEARING_AID_WIRELESS;
import static com.ttq.product.TagFixture.ADULT;
import static com.ttq.product.TagFixture.BASE;
import static com.ttq.product.TagFixture.CHILD;
import static com.ttq.product.TagFixture.CHILD_POUCH;
import static com.ttq.product.TagFixture.CLEAR;
import static com.ttq.product.TagFixture.CLEAR_POUCH;
import static com.ttq.product.TagFixture.ONE_PIECE;
import static com.ttq.product.TagFixture.OPAQUE;
import static com.ttq.product.TagFixture.OPAQUE_POUCH;
import static com.ttq.product.TagFixture.POUCH;
import static com.ttq.product.TagFixture.TWO_LAYER_POUCH;
import static com.ttq.product.TagFixture.TWO_PIECE;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductTagTest {

    private static final String OSTOMY = "hậu môn nhân tạo";

    @Autowired
    ProductController productController;

    @Autowired
    ProductTagController tagController;

    @Autowired
    BrandRepository brands;

    @Autowired
    ProductRepository products;

    @Autowired
    TagRepository tags;

    @Autowired
    ProductTagRepository productTags;

    @BeforeEach
    void catalog() {
        TagFixture.load(brands, products, tags, productTags);
    }

    private List<String> search(String q, String... tagCodes) {
        return productController.list(null, null, null, q, List.of(tagCodes), null).stream()
                .map(ProductView::code)
                .toList();
    }

    @Test
    void aProductMustCarryATagOfEveryGroupAskedAbout() {
        assertThat(search(OSTOMY, ONE_PIECE)).containsExactlyInAnyOrder(OPAQUE_POUCH, CLEAR_POUCH, CHILD_POUCH);
        assertThat(search(OSTOMY, ONE_PIECE, OPAQUE)).containsExactly(OPAQUE_POUCH);
        assertThat(search(OSTOMY, ONE_PIECE, CHILD, OPAQUE)).isEmpty();
    }

    @Test
    void tagsOfOneGroupAreAlternatives() {
        assertThat(search(OSTOMY, OPAQUE, CLEAR))
                .containsExactlyInAnyOrder(OPAQUE_POUCH, CLEAR_POUCH, TWO_LAYER_POUCH);
        // A two-layer pouch carries both, so it is found either way.
        assertThat(search(OSTOMY, CLEAR)).containsExactlyInAnyOrder(CLEAR_POUCH, TWO_LAYER_POUCH);
    }

    @Test
    void tagsNoProductOfTheSearchCarriesAreIgnored() {
        // Left over from another search, or unknown: they do not empty the list.
        assertThat(search("trợ thính", ONE_PIECE)).containsExactlyInAnyOrder(HEARING_AID_WIRED, HEARING_AID_WIRELESS);
        assertThat(search(OSTOMY, ONE_PIECE, "TAG-UNKNOWN"))
                .containsExactlyInAnyOrder(OPAQUE_POUCH, CLEAR_POUCH, CHILD_POUCH);
        assertThat(search(OSTOMY, ONE_PIECE.toLowerCase())).hasSize(3);
    }

    @Test
    void tagsAreListedInOrderWithTheProductsPickingEachWouldLeave() {
        List<ProductTagView> listed = tagController.list(null, null, null, OSTOMY, List.of(ONE_PIECE));

        assertThat(listed).extracting(ProductTagView::code).startsWith(POUCH).contains(OPAQUE, CLEAR);
        Map<String, Long> counts = listed.stream()
                .collect(Collectors.toMap(ProductTagView::code, ProductTagView::count));
        assertThat(counts).containsEntry(POUCH, 3L).containsEntry(ADULT, 2L).containsEntry(CHILD, 1L)
                .containsEntry(OPAQUE, 1L).containsEntry(CLEAR, 1L)
                // Its own group is left out, so the other answer still counts what it would switch to.
                .containsEntry(ONE_PIECE, 3L).containsEntry(TWO_PIECE, 2L);
        assertThat(listed).filteredOn(t -> t.code().equals(CHILD)).singleElement()
                .satisfies(t -> {
                    assertThat(t.group()).isEqualTo("Đối tượng");
                    assertThat(t.name()).isEqualTo("Trẻ em");
                    assertThat(t.synonyms()).contains("em bé");
                });
    }

    @Test
    void onlyTagsTheSearchedProductsCarryAreListed() {
        assertThat(tagController.list(null, null, null, "trợ thính", null)).isEmpty();
        assertThat(tagController.list(null, null, null, "đế rời", null)).extracting(ProductTagView::code)
                .containsExactlyInAnyOrder(TagFixture.BASE_PLATE, TWO_PIECE, ADULT);
        assertThat(tagController.list(null, null, null, null, null)).extracting(ProductTagView::code)
                .contains(POUCH, OPAQUE, CHILD);
        assertThat(search("đế rời")).containsExactly(BASE);
    }
}
