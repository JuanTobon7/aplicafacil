package com.aplicafacil.auth.api.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Página de login (el POST /login lo procesa Spring Security). */
@Controller
class LoginController {

    @GetMapping("/login")
    String login(@RequestParam(required = false) String error, HttpServletRequest request, Model model) {
        if (error != null) {
            model.addAttribute("errorMessage", errorMessage(request.getSession(false)));
        }
        return "login";
    }

    /** Destino si alguien inicia sesión sin venir de un cliente OAuth. */
    @GetMapping("/")
    String home(@AuthenticationPrincipal UserDetails user, Model model) {
        model.addAttribute("userId", user.getUsername());
        return "home";
    }

    private static String errorMessage(HttpSession session) {
        Object exception = session == null ? null : session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        if (exception instanceof LockedException) {
            return "Cuenta bloqueada temporalmente por demasiados intentos fallidos. Intenta de nuevo en 15 minutos.";
        }
        if (exception instanceof DisabledException) {
            return "Esta cuenta está deshabilitada.";
        }
        if (exception instanceof AuthenticationException) {
            return "Email o contraseña incorrectos.";
        }
        return "No se pudo iniciar sesión.";
    }
}
