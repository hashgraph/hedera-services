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

package com.swirlds.platform.test.event.creation;

import com.swirlds.common.system.EventCreationRule;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.platform.components.EventCreationRules;
import com.swirlds.platform.event.creation.LoggingEventCreationRules;
import com.swirlds.platform.event.creation.ParentBasedCreationRule;
import com.swirlds.platform.event.creation.StaticCreationRules;
import com.swirlds.platform.internal.EventImpl;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EventCreationRulesTest {
    private static final EventCreationRule PASS_1 = () -> EventCreationRuleResponse.PASS;
    private static final EventCreationRule CREATE_1 = () -> EventCreationRuleResponse.CREATE;
    private static final EventCreationRule DONT_1 = () -> EventCreationRuleResponse.DONT_CREATE;

    private static final ParentBasedCreationRule PASS_2 = (s, o) -> EventCreationRuleResponse.PASS;
    private static final ParentBasedCreationRule CREATE_2 = (s, o) -> EventCreationRuleResponse.CREATE;
    private static final ParentBasedCreationRule DONT_2 = (s, o) -> EventCreationRuleResponse.DONT_CREATE;

    private static void check(final EventCreationRules rules, final EventCreationRuleResponse expectedResponse) {
        Assertions.assertEquals(expectedResponse, rules.shouldCreateEvent());
        Assertions.assertEquals(expectedResponse, rules.shouldCreateEvent(null, null));
    }

    private static void check(
            final List<EventCreationRule> basicRules,
            final List<ParentBasedCreationRule> parentRules,
            final EventCreationRuleResponse expectedResponse) {
        check(new EventCreationRules(basicRules, parentRules), expectedResponse);
        check(LoggingEventCreationRules.create(basicRules, parentRules), expectedResponse);
    }

    private static void check(
            final List<EventCreationRule> basicRules, final EventCreationRuleResponse expectedResponse) {
        check(new EventCreationRules(basicRules), expectedResponse);
    }

    @Test
    void noRules() {
        check(List.of(), EventCreationRuleResponse.PASS);
        check(List.of(), List.of(), EventCreationRuleResponse.PASS);
    }

    @Test
    void pass() {
        check(List.of(PASS_1, PASS_1, PASS_1), List.of(PASS_2, PASS_2), EventCreationRuleResponse.PASS);
    }

    @Test
    void create() {
        check(List.of(PASS_1, CREATE_1, PASS_1), List.of(CREATE_2, PASS_2), EventCreationRuleResponse.CREATE);
    }

    @Test
    void dontCreate() {
        check(List.of(PASS_1, PASS_1, DONT_1), List.of(DONT_2, PASS_2), EventCreationRuleResponse.DONT_CREATE);
    }

    @Test
    void nullOtherParent() {
        Assertions.assertEquals(EventCreationRuleResponse.DONT_CREATE, StaticCreationRules.nullOtherParent(null, null));
        Assertions.assertEquals(
                EventCreationRuleResponse.PASS,
                StaticCreationRules.nullOtherParent(null, Mockito.mock(EventImpl.class)));
    }
}
