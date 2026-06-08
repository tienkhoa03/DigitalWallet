package com.digitalwallet.account.api;

import com.digitalwallet.account.dto.CreateAccountRequest;
import com.digitalwallet.account.dto.CreateAccountResponse;
import com.digitalwallet.account.service.AccountService;

import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code POST /accounts} — FR1.1 signup. Public endpoint (no JWT required) per the
 * default-deny rule's public list ({@code .claude/rules/security.md §3}).
 */
@Path(AccountResource.AccountPaths.BASE)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class AccountResource {

    public static final class AccountPaths {
        public static final String BASE = "/accounts";

        private AccountPaths() {
        }
    }

    private final AccountService accountService;

    public AccountResource(AccountService accountService) {
        this.accountService = accountService;
    }

    @POST
    public Response signup(@Valid CreateAccountRequest request) {
        CreateAccountResponse body = accountService.signup(request);
        return Response.status(Response.Status.CREATED).entity(body).build();
    }
}
