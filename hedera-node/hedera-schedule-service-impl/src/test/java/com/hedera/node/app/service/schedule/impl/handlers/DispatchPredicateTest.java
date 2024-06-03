/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.handlers;

import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hedera.test.utils.KeyUtils.A_THRESHOLD_KEY;
import static com.hedera.test.utils.KeyUtils.B_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.Key;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DispatchPredicateTest {

    private Set<Key> validKeys;
    private DispatchPredicate predicate;

    @BeforeEach
    void setUp() {
        validKeys = new HashSet<>();
        validKeys.add(A_COMPLEX_KEY);
        validKeys.add(A_THRESHOLD_KEY);
        predicate = new DispatchPredicate(validKeys);
    }

    @Test
    @DisplayName("Testing Constructor")
    void testConstructor() {
        assertThat(predicate).isNotNull();
        DispatchPredicate dispatchPredicate = new DispatchPredicate(validKeys);
        assertThat(predicate).isNotEqualTo(dispatchPredicate);
        assertThatThrownBy(() -> new DispatchPredicate(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Test for when predicate contains keys")
    void testContainsKey() {
        assertThat(predicate.test(A_COMPLEX_KEY)).isTrue();
        assertThat(predicate.test(B_COMPLEX_KEY)).isFalse();
    }

    @Test
    @DisplayName("Test for when predicate is missing keys")
    void testContainsKeyIsNotNull() {
        assertThatThrownBy(() -> predicate.test(null)).isInstanceOf(NullPointerException.class);
    }
}
