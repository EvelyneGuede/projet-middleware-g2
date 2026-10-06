package ci.hopital.rdv;

import com.nimbusds.jose.KeySourceException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Filtre placé devant toutes les URL /rdv/* : aucune requête n'atteint le code métier
 * sans jeton Keycloak valide. Il écrit aussi les traces AVANT / KEYCLOAK / APRES.
 */
@WebFilter(urlPatterns = "/rdv/*")
public class AuthFilter implements Filter {
    private static final Logger LOG = Logger.getLogger(AuthFilter.class.getName());

    @Override
    public void doFilter(ServletRequest rq, ServletResponse rs, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) rq;
        HttpServletResponse resp = (HttpServletResponse) rs;
        String label = req.getMethod() + " " + req.getRequestURI();

        LOG.info("[AVANT] " + label + " | thread Tomcat = " + Thread.currentThread().getName());
        try {
            String header = req.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                LOG.warning("[KEYCLOAK] jeton absent -> 401");
                resp.setHeader("WWW-Authenticate", "Bearer");
                Http.send(resp, 401, Http.error("Jeton manquant : authentifiez-vous auprès de Keycloak"));
                return;
            }
            JwtVerifier.Identity id;
            try {
                id = JwtVerifier.verify(header.substring(7).trim());
            } catch (KeySourceException e) {
                LOG.severe("[KEYCLOAK] clés publiques injoignables : " + e.getMessage());
                Http.send(resp, 503, Http.error("Keycloak injoignable, vérification impossible"));
                return;
            } catch (Exception e) {
                LOG.warning("[KEYCLOAK] jeton refusé -> 401 (" + e.getMessage() + ")");
                resp.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
                Http.send(resp, 401, Http.error("Jeton invalide ou expiré"));
                return;
            }
            LOG.info("[KEYCLOAK] jeton valide : utilisateur = " + id.username() + ", rôles = " + id.roles());
            req.setAttribute("identity", id);
            chain.doFilter(req, resp);
        } finally {
            LOG.info("[APRES] " + label + " -> HTTP " + resp.getStatus());
        }
    }
}
