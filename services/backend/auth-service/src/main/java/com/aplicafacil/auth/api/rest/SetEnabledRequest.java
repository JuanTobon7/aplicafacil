package com.aplicafacil.auth.api.rest;

import jakarta.validation.constraints.NotNull;

public record SetEnabledRequest(@NotNull Boolean enabled) {
}
