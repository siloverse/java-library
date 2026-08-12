package io.github.siloverse.messaging.core.helper;

import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.fixtures.*;

public class ConsumerDefinitionHelper {
    public static ConsumerDefinition createConsumerDef(
            String id,
            Class<?> messageClass
    ) {
        try {
            var method = OrderService.class.getDeclaredMethod("consume", messageClass);
            return new ConsumerDefinition(
                    id,
                    messageClass,
                    new Object(),
                    method,
                    -1
            );
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("Stub consumer method is missing", exception);
        }
    }

}
