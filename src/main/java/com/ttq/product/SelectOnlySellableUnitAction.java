package com.ttq.product;

import com.ttq.action.ActionContext;
import com.ttq.action.ActionResult;
import com.ttq.action.ProcessAction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Selects the sellable unit of the chosen variant when it is sold in only one, so the customer is
 * not asked to choose between one option. Does nothing when the variant has several units.
 */
@Component
class SelectOnlySellableUnitAction implements ProcessAction {

    private final SellableUnitRepository units;

    SelectOnlySellableUnitAction(SellableUnitRepository units) {
        this.units = units;
    }

    @Override
    public String name() {
        return "selectOnlySellableUnit";
    }

    @Override
    @Transactional(readOnly = true)
    public ActionResult execute(ActionContext context) {
        List<SellableUnit> sold = units.findByProductVariantCodeOrderByCodeAsc(
                context.requiredText("productVariantCode"));
        return sold.size() == 1
                ? new ActionResult(Map.of("sellableUnitCode", sold.getFirst().getCode()), null)
                : new ActionResult(Map.of(), null);
    }
}
