package com.aplicafacil.auth.api.rest;

import java.util.List;

public record UserPageResponse(List<UserResponse> items, int page, int size, long total) {
}
