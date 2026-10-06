package ci.hopital.rdv;

/** Paramètres de l'application, lus dans les variables d'environnement (valeurs par défaut pour la démo). */
final class Config {
    private Config() {}

    // "host.docker.internal" = le PC Windows, vu depuis le conteneur Tomcat
    static final String DB_URL = env("DB_URL", "jdbc:postgresql://host.docker.internal:5432/hopital");
    static final String DB_USER = env("DB_USER", "hopital");
    static final String DB_PASSWORD = env("DB_PASSWORD", "hopital123");

    static final String RABBIT_HOST = env("RABBIT_HOST", "host.docker.internal");
    static final int RABBIT_PORT = Integer.parseInt(env("RABBIT_PORT", "5672"));
    static final String RABBIT_USER = env("RABBIT_USER", "admin");
    static final String RABBIT_PASSWORD = env("RABBIT_PASSWORD", "admin123");
    static final String EXCHANGE = env("RABBIT_EXCHANGE", "rdv.events");

    // Clés publiques de Keycloak (pour vérifier la signature des jetons)
    static final String JWKS_URL = env("KEYCLOAK_JWKS_URL",
            "http://host.docker.internal:8081/realms/hopital/protocol/openid-connect/certs");
    // Émetteur écrit dans le jeton (adresse utilisée par le client pour obtenir le jeton)
    static final String ISSUER = env("KEYCLOAK_ISSUER", "http://localhost:8081/realms/hopital");

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
