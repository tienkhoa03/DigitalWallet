package com.digitalwallet.user.api;

import com.digitalwallet.user.api.dto.CreateUserRequest;
import com.digitalwallet.user.api.dto.CreateUserResponse;
import com.digitalwallet.user.service.UserService;

import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code POST /users} — FR1.1 signup. Public endpoint (no JWT required) per the
 * default-deny rule's public list ({@code .claude/rules/security.md §3}).
 */
@Path(UserResource.UserPaths.BASE)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class UserResource {

    public static final class UserPaths {
        public static final String BASE = "/users";

        private UserPaths() {
        }
    }

    private final UserService userService;

    public UserResource(UserService userService) {
        this.userService = userService;
    }

    @POST
    public Response signup(@Valid CreateUserRequest request) {
        CreateUserResponse body = userService.signup(request);
        return Response.status(Response.Status.CREATED).entity(body).build();
    }
}
