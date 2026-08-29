package io.github.siloverse.messaging.core.dispatch;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;

import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.error.MessagingException;

public class DefaultMessageDispatcher implements MessageDispatcher {

    public void dispatch(ConsumerDefinition def, Map<String, String> messageHeaders, Object message) {

        try {
            def.method().invoke(def.bean(), message);
        } catch (InvocationTargetException e) {
            throw rethrow(Objects.requireNonNullElse(e.getCause(), e));
        } catch (IllegalAccessException e) {
            throw new MessagingException(
                    "Cannot access consumer method " + def.id()
                            + " -- registration should have made it accessible. This is a scanner bug.", e);
        }
    }
}

