package io.github.siloverse.messaging.core.dispatch;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.error.ConsumerInvocationException;
import io.github.siloverse.messaging.core.error.MessagingException;

public class MessageDispatcher {

    public void dispatch(ConsumerDefinition def, Object message) {

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

    private static RuntimeException rethrow(Throwable cause) {
        if (cause instanceof RuntimeException re) {
            return re;                              // unchecked → rethrow as-is
        }
        if (cause instanceof Error error) {
            throw error;                            // OutOfMemoryError etc. → never wrap, let it fly
        }
        return new ConsumerInvocationException(     // checked → wrap once
                "Consumer threw a checked exception", cause);
    }
}
