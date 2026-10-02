package com.aplicafacil.auth.api.web;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.aplicafacil.auth.application.RegisterUserUseCase;
import com.aplicafacil.auth.application.exception.EmailAlreadyRegisteredException;

/**
 * Registro de cuentas. Es una página del Authorization Server (no un endpoint
 * del front) porque la contraseña solo debe escribirse aquí. Si el usuario
 * venía de /oauth2/authorize, tras registrarse e iniciar sesión vuelve al flujo.
 */
@Controller
@RequestMapping("/register")
class RegistrationController {

    private final RegisterUserUseCase registerUser;

    RegistrationController(RegisterUserUseCase registerUser) {
        this.registerUser = registerUser;
    }

    @GetMapping
    String form(Model model) {
        model.addAttribute("form", new RegistrationForm());
        return "register";
    }

    @PostMapping
    String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult errors) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            errors.rejectValue("confirmPassword", "mismatch", "Las contraseñas no coinciden");
        }
        if (errors.hasErrors()) {
            return "register";
        }
        try {
            registerUser.register(RegisterUserUseCase.Command.withUserRole(
                    form.getEmail(), form.getPassword(), form.getFirstName(), form.getLastName()));
        } catch (EmailAlreadyRegisteredException e) {
            errors.rejectValue("email", "taken", "Ya existe una cuenta con ese email");
            return "register";
        } catch (IllegalArgumentException e) {
            // política de contraseñas u otra regla del dominio
            errors.reject("invalid", e.getMessage());
            return "register";
        }
        return "redirect:/login?registered";
    }
}
