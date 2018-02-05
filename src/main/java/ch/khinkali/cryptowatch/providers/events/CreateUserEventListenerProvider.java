package ch.khinkali.cryptowatch.providers.events;

import lombok.NoArgsConstructor;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@NoArgsConstructor
public class CreateUserEventListenerProvider implements EventListenerProvider {

    private Logger logger = Logger.getLogger(CreateUserEventListenerProvider.class.getName());
    private KafkaProducer producer;
    private String topic;

    @Override
    public void onEvent(final Event event) {
        if (event.getType().equals(EventType.REGISTER)) {
            CompletableFuture
                    .runAsync(() -> createUser(event));
        }
    }

    private void createUser(Event event) {
        String userId = event.getUserId();
        String username = event.getDetails().get("username");

        logger.info("userId: " + userId);
        logger.info("username: " + username);
        publish(new UserEvent(userId, username));
    }

    private Properties getKafkaProperties() {
        Properties kafkaProperties = new Properties();
        kafkaProperties.put("bootstrap.servers", System.getenv("KAFKA_ADDRESS"));
        kafkaProperties.put("users.topic", "users");
        kafkaProperties.put("batch.size", 16384);
        kafkaProperties.put("linger.ms", 0);
        kafkaProperties.put("buffer.memory", 33554432);
        kafkaProperties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProperties.put("value.serializer", "ch.khinkali.cryptowatch.sink.events.control.EventSerializer");
        return kafkaProperties;
    }

    private void initKafkaProducer() {
        if (producer != null) {
            return;
        }
        Properties kafkaProperties = getKafkaProperties();
        kafkaProperties.put("transactional.id", UUID.randomUUID().toString());
        producer = new KafkaProducer<>(kafkaProperties);
        topic = kafkaProperties.getProperty("users.topic");
        producer.initTransactions();
    }

    public void publish(UserEvent event) {
        initKafkaProducer();
        final ProducerRecord<String, UserEvent> record = new ProducerRecord<>(topic, event);
        try {
            producer.beginTransaction();
            producer.send(record);
            producer.commitTransaction();
        } catch (ProducerFencedException e) {
            producer.close();
        } catch (KafkaException e) {
            producer.abortTransaction();
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
    }

    @Override
    public void close() {
    }

}