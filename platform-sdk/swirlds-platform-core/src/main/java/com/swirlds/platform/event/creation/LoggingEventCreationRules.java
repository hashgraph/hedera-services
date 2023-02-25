/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.event.creation;

import static com.swirlds.common.system.EventCreationRuleResponse.DONT_CREATE;

import com.swirlds.common.system.EventCreationRule;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.components.EventCreationRules;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Wraps {@link EventCreationRules}s and adds log messages that summarise which throttles prevent event creation. Useful
 * for debugging, should be eventually replaced with metrics.
 */
public final class LoggingEventCreationRules extends EventCreationRules {
    private static final Logger logger = LogManager.getLogger(LoggingEventCreationRules.class);
    private static final Duration logEvery = Duration.ofSeconds(3);
    private final List<RuleWrapper> wrappedRules;
    private Instant nextLog = Instant.now().plus(logEvery);

    public static LoggingEventCreationRules create(
            final List<EventCreationRule> basicRules, final List<ParentBasedCreationRule> parentRules) {
        return new LoggingEventCreationRules(
                basicRules.stream().map(RuleWrapper::new).toList(),
                parentRules.stream().map(RuleWrapper::new).toList());
    }

    private LoggingEventCreationRules(final List<RuleWrapper> wrappedBasic, final List<RuleWrapper> wrappedParent) {
        super(wrappedBasic, wrappedParent);
        this.wrappedRules =
                Stream.concat(wrappedBasic.stream(), wrappedParent.stream()).toList();
    }

    @Override
    public EventCreationRuleResponse shouldCreateEvent() {
        try {
            return super.shouldCreateEvent();
        } finally {
            maybeLog();
        }
    }

    @Override
    public EventCreationRuleResponse shouldCreateEvent(final BaseEvent selfParent, final BaseEvent otherParent) {
        try {
            return super.shouldCreateEvent(selfParent, otherParent);
        } finally {
            maybeLog();
        }
    }

    private void maybeLog() {
        if (nextLog.isBefore(Instant.now())) {
            logger.info(
                    LogMarker.EVENT_CREATION_THROTTLE.getMarker(),
                    "Event creation throttles counts:\n{}",
                    this::throttlesToString);
            nextLog = Instant.now().plus(logEvery);
        }
    }

    public String throttlesToString() {
        final StringBuilder sb = new StringBuilder();
        wrappedRules.forEach(r -> sb.append(String.format("%5d", r.getResetDontCreateCount()))
                .append(" - ")
                .append(r.getName())
                .append('\n'));
        return sb.toString();
    }

    private static class RuleWrapper implements EventCreationRule, ParentBasedCreationRule {
        private final String name;
        private final EventCreationRule rule;
        private final ParentBasedCreationRule parentRule;
        int dontCreateCount;

        public RuleWrapper(final EventCreationRule rule) {
            this(rule, null);
        }

        public RuleWrapper(final ParentBasedCreationRule rule) {
            this(null, rule);
        }

        private RuleWrapper(final EventCreationRule rule, final ParentBasedCreationRule parentRule) {
            if (rule != null) {
                name = rule.getClass().getSimpleName();
            } else if (parentRule != null) {
                name = parentRule.getClass().getSimpleName();
            } else {
                throw new IllegalArgumentException();
            }
            this.rule = rule;
            this.parentRule = parentRule;
            this.dontCreateCount = 0;
        }

        @Override
        public EventCreationRuleResponse shouldCreateEvent() {
            return count(rule.shouldCreateEvent());
        }

        @Override
        public EventCreationRuleResponse shouldCreateEvent(final BaseEvent selfParent, final BaseEvent otherParent) {
            return count(parentRule.shouldCreateEvent(selfParent, otherParent));
        }

        private EventCreationRuleResponse count(final EventCreationRuleResponse response) {
            if (response == DONT_CREATE) {
                dontCreateCount++;
            }
            return response;
        }

        public String getName() {
            return name;
        }

        public int getResetDontCreateCount() {
            final int ret = dontCreateCount;
            dontCreateCount = 0;
            return ret;
        }
    }
}
