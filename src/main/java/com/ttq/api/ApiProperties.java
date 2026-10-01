package com.ttq.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Settings for the calls a process may make.
 *
 * @param baseUrl      what {@code ${chatbot.api.base-url}} in the catalog resolves to
 * @param allowedHosts hosts the application may call. Catalog files decide which URLs are called,
 *                     so this is the boundary that keeps an edited or mistyped catalog from
 *                     turning the server into a client for somewhere it should never reach.
 *                     An entry may be a host ({@code api.bank.example}), a host with a port
 *                     ({@code api.bank.example:8443}) or a wildcard ({@code *.bank.example}).
 */
@ConfigurationProperties("chatbot.api")
public record ApiProperties(
        String baseUrl,
        @DefaultValue({"localhost", "127.0.0.1"}) List<String> allowedHosts) {

    /** @throws ApiCallException when the url may not be called */
    public void verifyAllowed(String apiId, URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ApiCallException("Api '%s' url '%s' is not http(s)".formatted(apiId, uri), null);
        }
        if (uri.getHost() == null) {
            throw new ApiCallException("Api '%s' url '%s' has no host".formatted(apiId, uri), null);
        }
        if (!allows(uri.getHost(), uri.getPort())) {
            throw new ApiCallException(
                    "Api '%s' calls host '%s', which is not in chatbot.api.allowed-hosts %s"
                            .formatted(apiId, uri.getHost(), allowedHosts), null);
        }
    }

    public boolean allows(String host, int port) {
        String target = host.toLowerCase(Locale.ROOT);
        return allowedHosts.stream().anyMatch(entry -> matches(entry.strip().toLowerCase(Locale.ROOT), target, port));
    }

    private static boolean matches(String entry, String host, int port) {
        if (entry.isEmpty()) {
            return false;
        }
        int colon = entry.lastIndexOf(':');
        if (colon > 0 && entry.substring(colon + 1).matches("\\d+")) {
            // An entry with a port pins both; a url without a port cannot match it.
            return Integer.parseInt(entry.substring(colon + 1)) == port
                    && matchesHost(entry.substring(0, colon), host);
        }
        return matchesHost(entry, host);
    }

    private static boolean matchesHost(String entry, String host) {
        if (entry.startsWith("*.")) {
            String domain = entry.substring(2);
            return host.equals(domain) || host.endsWith("." + domain);
        }
        return entry.equals(host);
    }
}
