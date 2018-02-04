package ch.khinkali.cryptowatch.providers.events;

import lombok.NoArgsConstructor;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@NoArgsConstructor
public class CreateUserEventListenerProvider implements EventListenerProvider {

    Logger logger = Logger.getLogger(CreateUserEventListenerProvider.class.getName());

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
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
    }

    @Override
    public void close() {
    }

}