package com.ttq.action;

import com.ttq.api.ApiCallExecutor;
import com.ttq.api.ApiDefinition;
import org.springframework.web.client.HttpClientErrorException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs an API from the catalog as a process action: build the request from the collected data,
 * capture what the response returns, and hand back the line to append to the reply.
 */
public class CatalogApiAction implements ProcessAction {

    private final ApiDefinition api;
    private final ApiCallExecutor executor;

    public CatalogApiAction(ApiDefinition api, ApiCallExecutor executor) {
        this.api = api;
        this.executor = executor;
    }

    @Override
    public String name() {
        return api.id();
    }

    @Override
    public ActionResult execute(ActionContext context) {
        Map<String, Object> values = new LinkedHashMap<>(context.collectedData());
        values.put("$conversationId", context.conversationId());
        values.put("$processId", context.process().id());

        Object response;
        try {
            response = executor.call(api, values);
        } catch (RuntimeException e) {
            if (rejected(e)) {
                throw new ActionFailedException(e.getMessage(), e,
                        ApiCallExecutor.template(api.rejectedMessage(), values), api.clearOnRejection());
            }
            throw new ActionFailedException(e.getMessage(), e);
        }

        Map<String, Object> captured = new LinkedHashMap<>();
        api.capture().forEach((field, path) -> {
            Object value = ApiCallExecutor.path(response, path);
            if (value != null) {
                captured.put(field, value);
            }
        });
        values.putAll(captured);

        return new ActionResult(captured, ApiCallExecutor.template(api.message(), values));
    }

    /** True when the API answered and refused the request, as opposed to being unreachable or broken. */
    private static boolean rejected(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpClientErrorException) {
                return true;
            }
        }
        return false;
    }
}
