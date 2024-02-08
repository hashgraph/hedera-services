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

package com.swirlds.common.wiring.schedulers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.wiring.schedulers.internal.Squelcher;
import com.swirlds.common.wiring.schedulers.internal.ThrowingSquelcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link ThrowingSquelcher} class.
 */
class ThrowingSquelcherTests {
    @Test
    @DisplayName("Standard operation")
    void standardOperation() {
        final Squelcher throwingSquelcher = new ThrowingSquelcher();
        assertFalse(throwingSquelcher.shouldSquelch());

        assertThrows(UnsupportedOperationException.class, throwingSquelcher::startSquelching);
        assertThrows(UnsupportedOperationException.class, throwingSquelcher::stopSquelching);

        assertFalse(throwingSquelcher.shouldSquelch());
    }
}
