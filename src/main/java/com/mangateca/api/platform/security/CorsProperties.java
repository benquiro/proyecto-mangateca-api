package com.mangateca.api.platform.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origenes CORS permitidos ({@code mangateca.cors.allowed-origins}). Una lista vacia significa
 * que no se permite ningun origen. Nunca se admite el comodin {@code *}.
 */
@ConfigurationProperties("mangateca.cors")
public record CorsProperties(@Nullable List<String> allowedOrigins) {

	private static final String PROPERTY = "mangateca.cors.allowed-origins";

	public CorsProperties {
		allowedOrigins = (allowedOrigins == null) ? List.of() : List.copyOf(allowedOrigins);
		for (String origin : allowedOrigins) {
			validate(origin);
		}
	}

	@Override
	public List<String> allowedOrigins() {
		return (this.allowedOrigins == null) ? List.of() : this.allowedOrigins;
	}

	private static void validate(String origin) {
		if (!isValidOrigin(origin)) {
			throw new IllegalArgumentException("Valor invalido en la propiedad " + PROPERTY + ": '" + origin
					+ "'. Se esperaba scheme://host[:port] con http o https, sin ruta ni barra final, y nunca '*'.");
		}
	}

	private static boolean isValidOrigin(String origin) {
		if (origin.equals("*")) {
			return false;
		}
		try {
			URI uri = new URI(origin);
			String scheme = (uri.getScheme() == null) ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			String path = uri.getRawPath();
			return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null
					&& uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
					&& (path == null || path.isEmpty());
		}
		catch (URISyntaxException ex) {
			return false;
		}
	}

}
