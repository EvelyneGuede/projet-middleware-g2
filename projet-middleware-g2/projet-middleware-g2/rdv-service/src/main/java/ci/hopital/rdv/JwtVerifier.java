package ci.hopital.rdv;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vérifie un jeton Keycloak : signature (clé publique récupérée sur Keycloak puis mise en cache),
 * date d'expiration, émetteur. Extrait ensuite le nom d'utilisateur et les rôles.
 */
final class JwtVerifier {
    private JwtVerifier() {}

    record Identity(String username, Set<String> roles) {}

    private static final ConfigurableJWTProcessor<SecurityContext> PROCESSOR = build();

    private static ConfigurableJWTProcessor<SecurityContext> build() {
        try {
            JWKSource<SecurityContext> keys =
                    JWKSourceBuilder.<SecurityContext>create(URI.create(Config.JWKS_URL).toURL()).build();
            ConfigurableJWTProcessor<SecurityContext> p = new DefaultJWTProcessor<>();
            p.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
            return p;
        } catch (Exception e) {
            throw new IllegalStateException("URL des clés Keycloak invalide : " + Config.JWKS_URL, e);
        }
    }

    static Identity verify(String token) throws Exception {
        JWTClaimsSet claims = PROCESSOR.process(token, null);
        if (!Config.ISSUER.equals(claims.getIssuer())) {
            throw new BadJWTException("Émetteur inattendu : " + claims.getIssuer());
        }
        Set<String> roles = new HashSet<>();
        Map<String, Object> realmAccess = claims.getJSONObjectClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> list) {
            for (Object r : list) {
                roles.add(String.valueOf(r));
            }
        }
        Object u = claims.getClaim("preferred_username");
        String username = (u != null) ? u.toString() : claims.getSubject();
        return new Identity(username, roles);
    }
}
