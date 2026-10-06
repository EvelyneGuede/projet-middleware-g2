package ci.hopital.rdv;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/** Petits utilitaires pour répondre en JSON. */
final class Http {
    private Http() {}

    static final ObjectMapper JSON = new ObjectMapper();

    static void send(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        JSON.writeValue(resp.getOutputStream(), body);
    }

    static Map<String, Object> error(String message) {
        return Map.of("erreur", message);
    }
}
