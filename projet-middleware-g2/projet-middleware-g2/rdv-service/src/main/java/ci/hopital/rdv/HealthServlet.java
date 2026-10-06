package ci.hopital.rdv;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Page publique qui rend visible le travail de Tomcat : serveur, thread de traitement, session. */
@WebServlet(urlPatterns = "/health")
public class HealthServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(true); // Tomcat crée et gère la session (cookie JSESSIONID)
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "UP");
        out.put("service", "rdv-service");
        out.put("serveur", getServletContext().getServerInfo());
        out.put("threadTomcat", Thread.currentThread().getName());
        out.put("sessionTomcat", session.getId());
        out.put("sessionNouvelle", session.isNew());
        Http.send(resp, 200, out);
    }
}
