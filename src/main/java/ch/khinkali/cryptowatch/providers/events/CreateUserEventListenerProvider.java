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
        publish(new UserCreated(userId, username));
    }

    private Properties getKafkaProperties() {
        Properties kafkaProperties = new Properties();
        kafkaProperties.put("bootstrap.servers", System.getenv("KAFKA_ADDRESS"));
        kafkaProperties.put("batch.size", 16384);
        kafkaProperties.put("linger.ms", 0);
        kafkaProperties.put("buffer.memory", 33554432);
        kafkaProperties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProperties.put("value.serializer", "ch.khinkali.cryptowatch.providers.events.EventSerializer");
        return kafkaProperties;
    }

    private void initKafkaProducer() {
        if (producer != null) {
            return;
        }
        Properties kafkaProperties = getKafkaProperties();
        kafkaProperties.put("transactional.id", UUID.randomUUID().toString());
        // https://stackoverflow.com/questions/37363119/kafka-producer-org-apache-kafka-common-serialization-stringserializer-could-no
        Thread.currentThread().setContextClassLoader(null);
        try {
            producer = new KafkaProducer<>(kafkaProperties);
        } catch (Exception e) {
            logger.severe(e.getMessage());
        }
        topic = "users";
        producer.initTransactions();
    }

    public void publish(UserCreated event) {
        initKafkaProducer();
        final ProducerRecord<String, UserCreated> record = new ProducerRecord<>(topic, event);
        try {
            producer.beginTransaction();
            producer.send(record);
            producer.commitTransaction();
        } catch (ProducerFencedException e) {
            logger.severe(e.getMessage());
            producer.close();
        } catch (KafkaException e) {
            logger.severe(e.getMessage());
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