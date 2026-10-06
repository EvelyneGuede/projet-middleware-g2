package ci.hopital.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

/**
 * Service Notification : lit les messages "rdv.created" dans la file notification.queue
 * et simule l'envoi d'un SMS / email au patient.
 *
 * - acquittement manuel (ack) : un message n'est retiré de la file qu'après traitement réussi
 * - message illisible : nack sans remise en file, il part dans la file d'erreurs notification.dlq
 * - si RabbitMQ est indisponible : nouvelles tentatives automatiques
 */
public class NotificationConsumer {
    static {
        // Une seule ligne par message de log
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %5$s%n");
    }

    private static final Logger LOG = Logger.getLogger(NotificationConsumer.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String HOST = env("RABBIT_HOST", "host.docker.internal");
    private static final int PORT = Integer.parseInt(env("RABBIT_PORT", "5672"));
    private static final String USER = env("RABBIT_USER", "admin");
    private static final String PASSWORD = env("RABBIT_PASSWORD", "admin123");
    private static final String QUEUE = env("RABBIT_QUEUE", "notification.queue");
    private static final long DELAY_MS = Long.parseLong(env("PROCESSING_DELAY_MS", "1000"));

    public static void main(String[] args) throws Exception {
        ConnectionFactory f = new ConnectionFactory();
        f.setHost(HOST);
        f.setPort(PORT);
        f.setUsername(USER);
        f.setPassword(PASSWORD);
        f.setConnectionTimeout(3000);
        f.setAutomaticRecoveryEnabled(true);   // reconnexion automatique si RabbitMQ redémarre
        f.setNetworkRecoveryInterval(5000);

        Connection conn = null;
        while (conn == null) {
            try {
                conn = f.newConnection("notification-consumer");
            } catch (Exception e) {
                LOG.warning("[RABBITMQ] indisponible, nouvelle tentative dans 5 s : " + e.getMessage());
                Thread.sleep(5000);
            }
        }

        Channel ch = conn.createChannel();
        ch.basicQos(1); // un seul message à la fois : on voit la file se vider progressivement
        LOG.info("[RABBITMQ] connecté à " + HOST + ":" + PORT + ", en attente de messages sur '" + QUEUE + "'");

        ch.basicConsume(QUEUE, false,
                (tag, delivery) -> handle(ch, delivery),
                tag -> LOG.warning("[RABBITMQ] consommateur annulé par le serveur"));

        new CountDownLatch(1).await(); // le programme reste actif
    }

    private static void handle(Channel ch, Delivery d) throws IOException {
        long tag = d.getEnvelope().getDeliveryTag();
        String body = new String(d.getBody(), StandardCharsets.UTF_8);
        try {
            LOG.info("[RABBITMQ] message reçu (clé " + d.getEnvelope().getRoutingKey() + ") : " + body);
            JsonNode n = JSON.readTree(body);
            if (n == null || !n.isObject()) {
                throw new IllegalArgumentException("le message n'est pas un objet JSON");
            }
            Thread.sleep(DELAY_MS); // simule le temps d'envoi du SMS / email

            LOG.info("[NOTIFICATION] SMS/email simulé envoyé à " + n.path("patient").asText("?")
                    + " : rendez-vous n°" + n.path("id").asText("?")
                    + " (" + n.path("motif").asText("?") + ") prévu le " + n.path("dateRdv").asText("?"));

            ch.basicAck(tag, false);
            LOG.info("[RABBITMQ] message acquitté (ack), retiré de la file");
        } catch (Exception e) {
            LOG.severe("[ERREUR] traitement impossible (" + e.getMessage() + ") -> envoi en file d'erreurs notification.dlq");
            ch.basicNack(tag, false, false); // pas de remise en file : le message va dans la DLQ
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
