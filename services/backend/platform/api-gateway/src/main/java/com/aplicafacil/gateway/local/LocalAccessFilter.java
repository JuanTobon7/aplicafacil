package com.aplicafacil.gateway.local;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

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
 * Primera línea de defensa de la API local, antes de cualquier ruta del gateway.
 * <ol>
 *   <li>Host permitido: bloquea DNS rebinding (un dominio externo que resuelve a 127.0.0.1).</li>
 *   <li>Origin permitido, si el navegador lo envía.</li>
 *   <li>{@code /api/**} exige {@code X-Local-Token}.</li>
 *   <li>{@code /__local-token} solo responde a procesos locales sin Origin (el proxy de Vite en desarrollo).</li>
 * </ol>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class LocalAccessFilter extends OncePerRequestFilter {

    static final String TOKEN_PATH = "/__local-token";

    private final LocalToken token;
    private final LocalAccessProperties properties;

    LocalAccessFilter(LocalToken token, LocalAccessProperties properties) {
        this.token = token;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rejection rejection = check(request);
        if (rejection != null) {
            reject(response, rejection.code(), rejection.detail());
        } else if (TOKEN_PATH.equals(request.getRequestURI())) {
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.getWriter().write(token.value());
        } else {
            chain.doFilter(request, response);
        }
    }

    /** @return por qué se rechaza, o {@code null} si pasa */
    private Rejection check(HttpServletRequest request) {
        Rejection foreign = checkHostAndOrigin(request);
        return foreign != null ? foreign : checkToken(request);
    }

    /** DNS rebinding y páginas ajenas: solo los hosts y orígenes de esta instalación. */
    private Rejection checkHostAndOrigin(HttpServletRequest request) {
        String host = request.getHeader(HttpHeaders.HOST);
        if (host == null || !properties.allowedHosts().contains(host.toLowerCase())) {
            return new Rejection("HOST_NOT_ALLOWED", "Host no permitido");
        }
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !properties.allowedOrigins().contains(origin)) {
            return new Rejection("ORIGIN_NOT_ALLOWED", "Origen no permitido");
        }
        return null;
    }

    /** El token se entrega solo a procesos locales y se exige en {@code /api/**}. */
    private Rejection checkToken(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String path = request.getRequestURI();
        if (TOKEN_PATH.equals(path) && (origin != null || !isLoopback(request.getRemoteAddr()))) {
            return new Rejection("FORBIDDEN", "Solo para procesos locales");
        }
        if (path.startsWith("/api/") && !token.matches(request.getHeader(LocalToken.HEADER))) {
            return new Rejection("LOCAL_TOKEN_REQUIRED",
                    "Falta el token local o cambió (¿se reinició el backend?). Recarga la página.");
        }
        return null;
    }

    private record Rejection(String code, String detail) {
    }

    private static boolean isLoopback(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static void reject(HttpServletResponse response, String code, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"status\":403,\"code\":\"" + code + "\",\"detail\":\"" + detail + "\"}");
    }
}
