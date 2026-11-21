package com.stdiscm.shareddb;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** True when {@code db.failover.replica-host} is set and not blank (an empty value means "no replica"). */
class ReplicaConfigured implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !context.getEnvironment().getProperty("db.failover.replica-host", "").isBlank();
    }
}
