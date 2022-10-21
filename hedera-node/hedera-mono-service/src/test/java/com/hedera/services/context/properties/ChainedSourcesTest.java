/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChainedSourcesTest {
    Set<String> fromFirst = Set.of("something", "anotherThing");
    Set<String> fromSecond = Set.of("somethingElse");
    String firstName = "something";
    String secondName = "somethingElse";
    Object firstValue = new Object(), secondValue = new Object();

    PropertySource first;
    PropertySource second;

    ChainedSources subject;

    @BeforeEach
    void setup() {
        first = mock(PropertySource.class);
        given(first.containsProperty(firstName)).willReturn(true);
        given(first.getProperty(firstName)).willReturn(firstValue);
        given(first.allPropertyNames()).willReturn(fromFirst);
        second = mock(PropertySource.class);
        given(second.containsProperty(secondName)).willReturn(true);
        given(second.getProperty(secondName)).willReturn(secondValue);
        given(second.allPropertyNames()).willReturn(fromSecond);

        subject = new ChainedSources(first, second);
    }

    @Test
    void ordersContains() {
        // when:
        var flag = subject.containsProperty(firstName);

        // then:
        assertTrue(flag);
        verify(first).containsProperty(firstName);
        verify(second, never()).containsProperty(firstName);
    }

    @Test
    void ordersContainsTwo() {
        // when:
        var flag = subject.containsProperty(secondName);

        // then:
        assertTrue(flag);
        verify(first).containsProperty(secondName);
        verify(second).containsProperty(secondName);
    }

    @Test
    void getsFromExpectedSource() {
        // expect:
        assertSame(firstValue, subject.getProperty(firstName));
        assertSame(secondValue, subject.getProperty(secondName));
    }

    @Test
    void getsAllProperties() {
        // expect;
        assertEquals(
                Set.of("somethingElse", "anotherThing", "something"), subject.allPropertyNames());
    }
}
