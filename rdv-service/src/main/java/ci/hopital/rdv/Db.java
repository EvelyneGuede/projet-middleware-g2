package ci.hopital.rdv;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/** Accès à PostgreSQL. */
final class Db {
    private static final Logger LOG = Logger.getLogger(Db.class.getName());

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Pilote PostgreSQL introuvable", e);
        }
    }

    private Db() {}

    static Connection get() throws SQLException {
        return DriverManager.getConnection(Config.DB_URL, Config.DB_USER, Config.DB_PASSWORD);
    }

    /** Crée la table au démarrage (avec nouvelles tentatives si PostgreSQL n'est pas encore prêt). */
    static void init() {
        for (int i = 1; i <= 10; i++) {
            try (Connection c = get(); Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS rendez_vous ("
                        + "id BIGSERIAL PRIMARY KEY,"
                        + "patient VARCHAR(100) NOT NULL,"
                        + "motif VARCHAR(255) NOT NULL,"
                        + "date_rdv VARCHAR(40) NOT NULL,"
                        + "statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',"
                        + "cree_le TIMESTAMP NOT NULL DEFAULT now())");
                LOG.info("[BASE] PostgreSQL prête, table rendez_vous disponible");
                return;
            } catch (SQLException e) {
                LOG.warning("[BASE] indisponible (tentative " + i + "/10) : " + e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        LOG.severe("[BASE] impossible de joindre PostgreSQL après 10 tentatives");
    }
}
