package ci.hopital.rdv;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;

/** Publie un événement JSON sur l'exchange rdv.events de RabbitMQ (protocole AMQP, port 5672). */
final class RabbitPublisher {
    private RabbitPublisher() {}

    static void publish(String routingKey, String json) throws Exception {
        ConnectionFactory f = new ConnectionFactory();
        f.setHost(Config.RABBIT_HOST);
        f.setPort(Config.RABBIT_PORT);
        f.setUsername(Config.RABBIT_USER);
        f.setPassword(Config.RABBIT_PASSWORD);
        f.setConnectionTimeout(3000);

        try (Connection conn = f.newConnection("rdv-service"); Channel ch = conn.createChannel()) {
            ch.confirmSelect(); // RabbitMQ confirme qu'il a bien pris le message en charge
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .deliveryMode(2) // message persistant (écrit sur disque)
                    .build();
            ch.basicPublish(Config.EXCHANGE, routingKey, props, json.getBytes(StandardCharsets.UTF_8));
            ch.waitForConfirmsOrDie(3000);
        }
    }
}
