package com.ttq.product;

import java.math.BigDecimal;

/**
 * A small catalog for tests, since the application no longer seeds one. Loading it again is a no-op,
 * so each test can call {@link #load} without caring which test ran first in the shared context.
 */
final class CatalogFixture {

    static final String OMRON = "BRAND-045";
    static final String RIONET = "BRAND-052";
    static final String THREE_M = "BRAND-001";
    static final String KHAC = "BRAND-031";

    static final String HEARING_AID_WIRED = "PRODUCT-00004";
    static final String HEARING_AID_WIRELESS = "PRODUCT-00005";
    static final String BLOOD_PRESSURE_MONITOR = "PRODUCT-00194";
    static final String FILM_DRESSING = "PRODUCT-00020";
    static final String EYE_MASK = "PRODUCT-00010";

    static final String MONITOR_WHITE = BLOOD_PRESSURE_MONITOR + "-001";
    static final String MASK_LAVENDER = EYE_MASK + "-001";
    static final String MASK_MUGWORT = EYE_MASK + "-002";

    private CatalogFixture() {
    }

    /** Variants and sellable units of the products {@link #load} adds; call that first. */
    static void loadVariants(ProductRepository products, ProductVariantRepository variants,
                             SellableUnitRepository units) {
        if (variants.findByCodeIgnoreCase(MONITOR_WHITE).isPresent()) {
            return;
        }
        Product monitor = products.findByCodeIgnoreCaseAndActiveTrue(BLOOD_PRESSURE_MONITOR).orElseThrow();
        Product mask = products.findByCodeIgnoreCaseAndActiveTrue(EYE_MASK).orElseThrow();

        ProductVariant monitorWhite = variants.save(
                new ProductVariant(MONITOR_WHITE, monitor, "Vòng bắp tay: 22 - 32 cm"));
        ProductVariant maskLavender = variants.save(
                new ProductVariant(MASK_LAVENDER, mask));
        ProductVariant maskMugwort = variants.save(
                new ProductVariant(MASK_MUGWORT, mask));

        units.save(new SellableUnit(MONITOR_WHITE + "-U01", monitorWhite, "Cái", 1, new BigDecimal("1250000")));
        units.save(new SellableUnit(MASK_LAVENDER + "-U01", maskLavender, "Cái", 1, new BigDecimal("30000")));
        units.save(new SellableUnit(MASK_LAVENDER + "-U02", maskLavender, "Hộp", 5, null));
        units.save(new SellableUnit(MASK_MUGWORT + "-U01", maskMugwort, "Hộp", 5, new BigDecimal("150000")));
    }

    /**
     * Attribute values for the variants {@link #loadVariants} adds: the monitor's size, the mask's color
     * and scent, the last two telling its variants apart. Call that first.
     */
    static void loadVariantAttributes(ProductRepository products, ProductVariantRepository variants,
                                      AttributeDefinitionRepository definitions,
                                      ProductAttributeRepository productAttributes,
                                      ProductVariantAttributeRepository variantAttributes) {
        if (definitions.findByCode("ATTR-27").isPresent()) {
            return;
        }
        AttributeDefinition size = definitions.save(
                new AttributeDefinition("ATTR-27", "Kích cỡ", "Size", "Danh sách", "Đai, vớ", "S; M; L", null));
        AttributeDefinition color = definitions.save(
                new AttributeDefinition("ATTR-24", "Màu sắc", "Color", "Danh sách", "Băng keo", "Tím; Xanh", null));
        AttributeDefinition scent = definitions.save(
                new AttributeDefinition("ATTR-T1", "Mùi hương", "Scent", "Danh sách", "Mặt nạ", null, null));

        Product monitor = products.findByCodeIgnoreCaseAndActiveTrue(BLOOD_PRESSURE_MONITOR).orElseThrow();
        Product mask = products.findByCodeIgnoreCaseAndActiveTrue(EYE_MASK).orElseThrow();
        productAttributes.save(new ProductAttribute(monitor, size, true));
        productAttributes.save(new ProductAttribute(mask, color, true, "Tím; Xanh",
                "Chỉ khác màu, công dụng như nhau - chọn theo sở thích.", null, null));
        productAttributes.save(new ProductAttribute(mask, scent, true));

        ProductVariant monitorWhite = variants.findByCodeIgnoreCase(MONITOR_WHITE).orElseThrow();
        ProductVariant maskLavender = variants.findByCodeIgnoreCase(MASK_LAVENDER).orElseThrow();
        ProductVariant maskMugwort = variants.findByCodeIgnoreCase(MASK_MUGWORT).orElseThrow();
        variantAttributes.save(new ProductVariantAttribute(monitorWhite, size, "M"));
        variantAttributes.save(new ProductVariantAttribute(maskLavender, color, "Tím"));
        variantAttributes.save(new ProductVariantAttribute(maskLavender, scent, "Oải hương"));
        variantAttributes.save(new ProductVariantAttribute(maskMugwort, color, "Xanh"));
        variantAttributes.save(new ProductVariantAttribute(maskMugwort, scent, "Ngải cứu"));
    }

    /**
     * What a customer comparing products is told about how the hearing aids differ in how they connect,
     * with the blood pressure monitor given a comparison pointing at the wireless one. Call
     * {@link #load} first.
     */
    static void loadComparison(ProductRepository products, AttributeDefinitionRepository definitions,
                               ProductAttributeRepository productAttributes) {
        if (definitions.findByCode("ATTR-17").isPresent()) {
            return;
        }
        AttributeDefinition connection = definitions.save(new AttributeDefinition(
                "ATTR-17", "Kết nối", "Connectivity", "Danh sách", "Máy trợ thính", "Có dây; Không dây", null));
        Product wired = products.findByCodeIgnoreCaseAndActiveTrue(HEARING_AID_WIRED).orElseThrow();
        Product wireless = products.findByCodeIgnoreCaseAndActiveTrue(HEARING_AID_WIRELESS).orElseThrow();
        Product monitor = products.findByCodeIgnoreCaseAndActiveTrue(BLOOD_PRESSURE_MONITOR).orElseThrow();
        productAttributes.save(new ProductAttribute(wired, connection, false, "Có dây", null,
                "So với không dây: giá thấp, dễ dùng; kém thẩm mỹ.", HEARING_AID_WIRELESS));
        productAttributes.save(new ProductAttribute(wireless, connection, false, "Không dây", null,
                "So với có dây: gọn, kín đáo; giá cao hơn.", HEARING_AID_WIRED));
        productAttributes.save(new ProductAttribute(monitor, connection, false, "Có dây",
                "Chọn loại nối dây nếu hay đo tại nhà.", "So với không dây: ít hỏng hơn.",
                HEARING_AID_WIRELESS + "; PRODUCT-99999"));
    }

    static void load(BrandRepository brands, ProductRepository products) {
        if (brands.findByCodeIgnoreCase(OMRON).isPresent()) {
            return;
        }
        Brand omron = brands.save(new Brand(OMRON, "Omron", "Nhật Bản"));
        Brand rionet = brands.save(new Brand(RIONET, "Rionet", "Nhật Bản"));
        Brand threeM = brands.save(new Brand(THREE_M, "3M™", "Mỹ"));
        Brand khac = brands.save(new Brand(KHAC, "Khác", null));

        products.save(new Product(HEARING_AID_WIRED, "[Rionet] Máy trợ thính có dây đeo - HA-20DX", rionet,
                "Máy trợ thính",
                "Máy trợ thính có dây Rionet HA-20DX khuếch đại âm thanh cho người nghe kém.", "cái"));
        products.save(new Product(HEARING_AID_WIRELESS, "[Rionet] Máy trợ thính không dây - HB-23P", rionet,
                "Máy trợ thính",
                "Máy trợ thính không dây Rionet HB-23P dạng móc vành tai.", "cái"));
        products.save(new Product(BLOOD_PRESSURE_MONITOR, "[Omron] Máy đo huyết áp bắp tay tự động - HEM-7156",
                omron, "Máy đo huyết áp",
                "Máy đo huyết áp bắp tay tự động Omron HEM-7156, lưu 60 kết quả.", "cái"));
        products.save(new Product(FILM_DRESSING, "[3M™] Băng dán vết thương trong suốt - Tegaderm™", threeM,
                "Băng gạc vết thương",
                "Băng dán trong suốt, chống thấm nước, thoáng khí.", "hộp"));
        products.save(new Product(EYE_MASK, "Mặt nạ xông hơi mắt ngải cứu - Mugwort Steam Eyes Mask", khac,
                "Chăm sóc da", null, "gói"));
    }
}
