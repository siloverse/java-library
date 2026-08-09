package io.github.siloverse.messaging.dispatch;

import io.github.siloverse.messaging.api.Message;

/**
 * Delivers a message to its consumers on the calling thread.
 */
public interface MessageDispatcher {

    /**
     * @param message the message to deliver
     */
    void dispatch(Message message);
}
