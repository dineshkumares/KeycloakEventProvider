package ch.khinkali.cryptowatch.providers.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class UserEvent {
    private String userId;
    private String username;
}
