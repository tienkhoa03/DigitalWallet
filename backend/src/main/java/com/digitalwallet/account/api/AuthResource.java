package com.digitalwallet.account.api;

import com.digitalwallet.account.dto.LoginRequest;
import com.digitalwallet.account.dto.LoginResponse;
import com.digitalwallet.account.service.AuthService;

import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code POST /auth/login} — issues a JWT for verified credentials. Public endpoint
 * per the default-deny rule's public list ({@code .claude/rules/security.md §3}).
 */
@Path(AuthResource.AuthPaths.BASE)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class AuthResource {

    public static final class AuthPaths {
        public static final String BASE = "/auth";
        public static final String LOGIN = "/login";

        private AuthPaths() {
        }
    }

    private final AuthService authService;

    public AuthResource(AuthService authService) {
        this.authService = authService;
    }

    @POST
    @Path(AuthPaths.LOGIN)
    public Response login(@Valid LoginRequest request) {
        LoginResponse body = authService.login(request);
        return Response.ok(body).build();
    }
}
