package com.aplicafacil.gateway.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sirve {@code index.html} del build de React para todas las rutas del front (SPA) e inyecta el token local
 * en un {@code <meta>}. Los archivos con extensión (JS, CSS, imágenes) los sirve el manejador de estáticos.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class SpaIndexFilter extends OncePerRequestFilter {

    private final LocalToken token;
    private final LocalAccessProperties properties;

    SpaIndexFilter(LocalToken token, LocalAccessProperties properties) {
        this.token = token;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String last = path.substring(path.lastIndexOf('/') + 1);
        return properties.frontDist() == null
                || !"GET".equals(request.getMethod())
                || path.startsWith("/api/") || path.startsWith("/actuator") || path.startsWith("/__")
                || last.contains(".");
    }

    private static final Pattern STALE_TOKEN = Pattern.compile("<meta\\s+name=\"af-local-token\"[^>]*>\\s*");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Path index = properties.frontDist().resolve("index.html");
        if (!Files.isRegularFile(index)) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "No se encontró el build del front (" + index + "). Ejecuta: npm --prefix apps/web run build");
            return;
        }
        // Si el build trae un token (de otro arranque), se quita: el front lee el primer <meta> y quedaría con uno viejo
        String html = STALE_TOKEN.matcher(Files.readString(index, StandardCharsets.UTF_8)).replaceAll("")
                .replace("</head>", "<meta name=\"af-local-token\" content=\"" + token.value() + "\" />\n</head>");
        response.setContentType(MediaType.TEXT_HTML_VALUE + ";charset=UTF-8");
        // El token cambia en cada arranque: el HTML nunca se cachea
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.getWriter().write(html);
    }
}
