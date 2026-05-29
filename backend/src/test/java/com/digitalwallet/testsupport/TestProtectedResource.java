package com.digitalwallet.testsupport;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Throwaway probe used by {@link com.digitalwallet.user.api.JwtVerifierIT} to confirm
 * that {@code @RolesAllowed} is wired against the {@code groups} claim. Only on the test
 * classpath ({@code src/test/java/}); never compiled into the production jar.
 */
@Path("/_test/protected")
public class TestProtectedResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @RolesAllowed("USER")
    public String hello() {
        return "ok";
    }
}
