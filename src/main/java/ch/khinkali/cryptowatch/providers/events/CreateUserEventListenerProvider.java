package ch.khinkali.cryptowatch.providers.events;

import ch.khinkali.cryptowatch.events.boundary.EventSerializer;
import ch.khinkali.cryptowatch.events.entity.UserCreated;
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
        publish(new UserCreated(userId, username));
    }

    private Properties getKafkaProperties() {
        Properties kafkaProperties = new Properties();
        kafkaProperties.put("bootstrap.servers", System.getenv("KAFKA_ADDRESS"));
        kafkaProperties.put("batch.size", 16384);
        kafkaProperties.put("linger.ms", 0);
        kafkaProperties.put("buffer.memory", 33554432);
        kafkaProperties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProperties.put("value.serializer", EventSerializer.class.getCanonicalName());
        kafkaProperties.put("transactional.id", UUID.randomUUID().toString());
        return kafkaProperties;
    }

    private void initKafkaProducer() {
        if (producer != null) {
            return;
        }
        // https://stackoverflow.com/questions/37363119/kafka-producer-org-apache-kafka-common-serialization-stringserializer-could-no
        Thread.currentThread().setContextClassLoader(null);
        try {
            producer = new KafkaProducer<>(getKafkaProperties());
        } catch (Exception e) {
            logger.severe(e.getMessage());
            e.printStackTrace();
        }
        producer.initTransactions();
    }

    public void publish(UserCreated event) {
        initKafkaProducer();
        final ProducerRecord<String, UserCreated> record = new ProducerRecord<>(UserCreated.TOPIC, event);
        try {
            producer.beginTransaction();
            producer.send(record);
            producer.commitTransaction();
        } catch (ProducerFencedException e) {
            logger.severe(e.getMessage());
            producer.close();
            e.printStackTrace();
        } catch (KafkaException e) {
            logger.severe(e.getMessage());
            producer.abortTransaction();
            e.printStackTrace();
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
    }

    @Override
    public void close() {
    }

}