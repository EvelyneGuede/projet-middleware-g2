package ci.hopital.rdv;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.util.logging.Logger;

/** Écoute le cycle de vie de l'application web : Tomcat appelle ces méthodes au démarrage et à l'arrêt. */
@WebListener
public class Startup implements ServletContextListener {
    private static final Logger LOG = Logger.getLogger(Startup.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent e) {
        LOG.info("[TOMCAT] Application rdv-service DEMARREE (serveur : "
                + e.getServletContext().getServerInfo() + ")");
        new Thread(Db::init, "db-init").start();
    }

    @Override
    public void contextDestroyed(ServletContextEvent e) {
        LOG.info("[TOMCAT] Application rdv-service ARRETEE");
    }
}
