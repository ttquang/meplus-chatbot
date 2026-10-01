package com.ttq.product;

/**
 * A few ostomy products with tags, on top of {@link CatalogFixture}. Loading it again is a no-op.
 * None of the other fixture products carry a tag, and all of these mention "hậu môn nhân tạo".
 */
final class TagFixture {

    static final String OPAQUE_POUCH = "PRODUCT-00901";
    static final String CLEAR_POUCH = "PRODUCT-00902";
    static final String CHILD_POUCH = "PRODUCT-00903";
    static final String TWO_LAYER_POUCH = "PRODUCT-00904";
    static final String BASE = "PRODUCT-00905";
    static final String POWDER = "PRODUCT-00906";

    static final String POUCH = "TAG-TUI";
    static final String BASE_PLATE = "TAG-DE";
    static final String ACCESSORY = "TAG-PK";
    static final String ONE_PIECE = "TAG-1M";
    static final String TWO_PIECE = "TAG-2M";
    static final String ADULT = "TAG-NL";
    static final String CHILD = "TAG-TE";
    static final String OPAQUE = "TAG-DUC";
    static final String CLEAR = "TAG-TRONG";

    private TagFixture() {
    }

    static void load(BrandRepository brands, ProductRepository products, TagRepository tags,
                     ProductTagRepository productTags) {
        CatalogFixture.load(brands, products);
        if (tags.findByCodeIgnoreCase(POUCH).isPresent()) {
            return;
        }
        Brand khac = brands.findByCodeIgnoreCase(CatalogFixture.KHAC).orElseThrow();
        Tag pouch = tags.save(new Tag(POUCH, "Loại sản phẩm", "Túi", "túi đựng phân", 10));
        Tag base = tags.save(new Tag(BASE_PLATE, "Loại sản phẩm", "Đế", "đế rời", 11));
        Tag accessory = tags.save(new Tag(ACCESSORY, "Loại sản phẩm", "Phụ kiện", "bột, keo", 12));
        Tag onePiece = tags.save(new Tag(ONE_PIECE, "Hệ", "1 mảnh", "túi liền đế", 20));
        Tag twoPiece = tags.save(new Tag(TWO_PIECE, "Hệ", "2 mảnh", "túi và đế rời", 21));
        Tag adult = tags.save(new Tag(ADULT, "Đối tượng", "Người lớn", null, 30));
        Tag child = tags.save(new Tag(CHILD, "Đối tượng", "Trẻ em", "bé, em bé", 31));
        Tag opaque = tags.save(new Tag(OPAQUE, "Độ trong", "Đục", "kín đáo, màu be", 40));
        Tag clear = tags.save(new Tag(CLEAR, "Độ trong", "Trong suốt", "trong, dễ quan sát", 41));

        tag(productTags, product(products, khac, OPAQUE_POUCH, "Túi hậu môn nhân tạo 1 mảnh loại đục"),
                pouch, onePiece, adult, opaque);
        tag(productTags, product(products, khac, CLEAR_POUCH, "Túi hậu môn nhân tạo 1 mảnh trong suốt"),
                pouch, onePiece, adult, clear);
        tag(productTags, product(products, khac, CHILD_POUCH, "Túi hậu môn nhân tạo 1 mảnh cho trẻ em"),
                pouch, onePiece, child);
        tag(productTags, product(products, khac, TWO_LAYER_POUCH, "Túi hậu môn nhân tạo 2 mảnh hai lớp"),
                pouch, twoPiece, adult, opaque, clear);
        tag(productTags, product(products, khac, BASE, "Đế rời túi hậu môn nhân tạo 2 mảnh"),
                base, twoPiece, adult);
        tag(productTags, product(products, khac, POWDER, "Bột hút ẩm chống loét hậu môn nhân tạo"),
                accessory);
    }

    private static Product product(ProductRepository products, Brand brand, String code, String name) {
        return products.save(new Product(code, name, brand, "Túi hậu môn nhân tạo", null, "cái"));
    }

    private static void tag(ProductTagRepository productTags, Product product, Tag... tags) {
        for (Tag tag : tags) {
            productTags.save(new ProductTag(product, tag));
        }
    }
}
