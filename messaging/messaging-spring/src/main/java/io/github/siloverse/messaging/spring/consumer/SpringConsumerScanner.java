package io.github.siloverse.messaging.spring.consumer;

import java.util.List;

import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.consumer.ConsumerMethodScanner;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;

public class SpringConsumerScanner {

    private final ConsumerMethodScanner scanner;

    public SpringConsumerScanner(ConsumerMethodScanner scanner) {
        this.scanner = scanner;
    }

    public List<ConsumerDefinition> scan(Object bean) {
        var targetClass = AopProxyUtils.ultimateTargetClass(bean);   // pre-step: unwrap
        return scanner.scan(bean, targetClass).stream()               // delegate: core does the work
                .map(def -> withInvocableMethod(def, bean))           // post-step: JDK-proxy fix
                .toList();
    }

    private ConsumerDefinition withInvocableMethod(ConsumerDefinition def, Object bean) {
        var invocable = AopUtils.selectInvocableMethod(def.method(), bean.getClass());
        return invocable == def.method() ? def
                : new ConsumerDefinition(def.id(), def.messageClass(), def.bean(),
                        invocable, def.contextParameterIndex());
    }
}
