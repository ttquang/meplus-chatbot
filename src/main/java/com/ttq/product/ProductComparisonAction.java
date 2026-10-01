package com.ttq.product;

import com.ttq.action.ActionContext;
import com.ttq.action.ActionResult;
import com.ttq.action.ProcessAction;
import com.ttq.product.ProductAttributeController.ProductAttributeView;
import com.ttq.product.ProductAttributeController.ProductRef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes how the chosen product compares with others of its category, from what the catalog holds
 * for each of its attributes, so the facts of a comparison are never made up by the model.
 */
@Component
class ProductComparisonAction implements ProcessAction {

    private final ProductAttributeController attributes;
    private final ProductRepository products;

    ProductComparisonAction(ProductAttributeController attributes, ProductRepository products) {
        this.attributes = attributes;
        this.products = products;
    }

    @Override
    public String name() {
        return "getProductComparison";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String productCode = context.requiredText("productCode");
        String name = products.findByCodeIgnoreCaseAndActiveTrue(productCode).map(Product::getName)
                .orElse(productCode);
        List<String> lines = new ArrayList<>();
        for (ProductAttributeView attribute : attributes.list(productCode)) {
            if (blank(attribute.comparison())) {
                continue;
            }
            StringBuilder line = new StringBuilder("• ").append(attribute.name());
            if (!blank(attribute.validValue())) {
                line.append(" (").append(attribute.validValue().strip()).append(")");
            }
            line.append(": ").append(attribute.comparison().strip());
            if (!attribute.relatedProducts().isEmpty()) {
                line.append("\n   Sản phẩm khác: ").append(attribute.relatedProducts().stream()
                        .map(ProductRef::name).collect(Collectors.joining("; ")));
            }
            if (!blank(attribute.guidance())) {
                line.append("\n   Cách chọn: ").append(attribute.guidance().strip());
            }
            lines.add(line.toString());
        }
        String message = lines.isEmpty()
                ? "Hiện chưa có thông tin so sánh cho " + name + "."
                : "So sánh " + name + " với các sản phẩm khác cùng danh mục:\n" + String.join("\n", lines);
        return new ActionResult(Map.of(), message);
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
