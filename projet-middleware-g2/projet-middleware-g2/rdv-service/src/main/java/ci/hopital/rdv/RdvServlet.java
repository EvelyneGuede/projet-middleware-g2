package ci.hopital.rdv;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API des rendez-vous (l'authentification est déjà faite par AuthFilter).
 *   POST   /rdv                  patient : prendre un rendez-vous
 *   GET    /rdv/mes-rdv          patient : ses rendez-vous
 *   GET    /rdv/agenda           medecin : agenda
 *   PUT    /rdv/{id}/valider     medecin : valider
 *   GET    /rdv                  agent   : tous les rendez-vous
 *   DELETE /rdv/{id}             agent   : supprimer
 */
@WebServlet(urlPatterns = "/rdv/*")
public class RdvServlet extends HttpServlet {
    private static final Logger LOG = Logger.getLogger(RdvServlet.class.getName());
    private static final String COLS = "id, patient, motif, date_rdv, statut, cree_le";

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JwtVerifier.Identity id = (JwtVerifier.Identity) req.getAttribute("identity");
        String m = req.getMethod();
        String p = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            if (m.equals("POST") && p.isEmpty()) {
                if (need(id, resp, "patient")) creer(id, req, resp);
            } else if (m.equals("GET") && p.equals("/mes-rdv")) {
                if (need(id, resp, "patient")) lister(resp, "WHERE patient = ?", id.username());
            } else if (m.equals("GET") && p.equals("/agenda")) {
                if (need(id, resp, "medecin")) lister(resp, "", null);
            } else if (m.equals("GET") && p.isEmpty()) {
                if (need(id, resp, "agent")) lister(resp, "", null);
            } else if (m.equals("PUT") && p.matches("/\\d+/valider")) {
                if (need(id, resp, "medecin")) valider(resp, Long.parseLong(p.split("/")[1]));
            } else if (m.equals("DELETE") && p.matches("/\\d+")) {
                if (need(id, resp, "agent")) supprimer(resp, Long.parseLong(p.substring(1)));
            } else {
                Http.send(resp, 404, Http.error("Route inconnue : " + m + " " + req.getRequestURI()));
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "[BASE] erreur SQL", e);
            Http.send(resp, 500, Http.error("Erreur base de données"));
        }
    }

    /** Contrôle d'accès par rôle : 403 si le rôle exigé est absent du jeton. */
    private static boolean need(JwtVerifier.Identity id, HttpServletResponse resp, String role) throws IOException {
        if (id.roles().contains(role)) return true;
        LOG.warning("[ACCES REFUSE] " + id.username() + " (rôles " + id.roles() + ") n'a pas le rôle '" + role + "' -> 403");
        Http.send(resp, 403, Http.error("Accès interdit : le rôle '" + role + "' est requis"));
        return false;
    }

    private void creer(JwtVerifier.Identity id, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, SQLException {
        JsonNode body;
        try {
            body = Http.JSON.readTree(req.getInputStream());
        } catch (IOException e) {
            Http.send(resp, 400, Http.error("Corps JSON invalide"));
            return;
        }
        String motif = text(body, "motif");
        String date = text(body, "date");
        if (motif.isEmpty() || date.isEmpty()) {
            Http.send(resp, 400, Http.error("Champs 'motif' et 'date' obligatoires"));
            return;
        }

        Map<String, Object> rdv;
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO rendez_vous(patient, motif, date_rdv) VALUES (?,?,?) RETURNING " + COLS)) {
            ps.setString(1, id.username());
            ps.setString(2, motif);
            ps.setString(3, date);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                rdv = row(rs);
            }
        }
        LOG.info("[BASE] rendez-vous enregistré : id=" + rdv.get("id") + ", patient=" + id.username());

        // Événement asynchrone vers Notification, via RabbitMQ
        String notification;
        try {
            Map<String, Object> event = new LinkedHashMap<>(rdv);
            event.put("evenement", "rdv.created");
            String json = Http.JSON.writeValueAsString(event);
            RabbitPublisher.publish("rdv.created", json);
            notification = "publiee";
            LOG.info("[RABBITMQ] message publié (rdv.created) : " + json);
        } catch (Exception e) {
            notification = "echec_publication";
            LOG.log(Level.SEVERE, "[RABBITMQ] échec de publication, le rendez-vous reste enregistré", e);
        }

        Map<String, Object> out = new LinkedHashMap<>(rdv);
        out.put("notification", notification);
        Http.send(resp, 201, out);
    }

    private void lister(HttpServletResponse resp, String where, String param) throws IOException, SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM rendez_vous " + where + " ORDER BY id")) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(row(rs));
            }
        }
        Http.send(resp, 200, list);
    }

    private void valider(HttpServletResponse resp, long id) throws IOException, SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE rendez_vous SET statut = 'VALIDE' WHERE id = ? RETURNING " + COLS)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) Http.send(resp, 200, row(rs));
                else Http.send(resp, 404, Http.error("Rendez-vous " + id + " introuvable"));
            }
        }
    }

    private void supprimer(HttpServletResponse resp, long id) throws IOException, SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM rendez_vous WHERE id = ?")) {
            ps.setLong(1, id);
            if (ps.executeUpdate() > 0) Http.send(resp, 200, Map.of("supprime", id));
            else Http.send(resp, 404, Http.error("Rendez-vous " + id + " introuvable"));
        }
    }

    private static String text(JsonNode body, String key) {
        return (body != null && body.hasNonNull(key)) ? body.get(key).asText().trim() : "";
    }

    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("patient", rs.getString("patient"));
        m.put("motif", rs.getString("motif"));
        m.put("dateRdv", rs.getString("date_rdv"));
        m.put("statut", rs.getString("statut"));
        m.put("creeLe", rs.getTimestamp("cree_le").toString());
        return m;
    }
}
