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

import com.swirlds.common.wiring.schedulers.internal.NoopSquelcher;
import com.swirlds.common.wiring.schedulers.internal.Squelcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link NoopSquelcher} class.
 */
class NoopSquelcherTests {
    @Test
    @DisplayName("Standard operation")
    void standardOperation() {
        final Squelcher noopSquelcher = new NoopSquelcher();
        assertFalse(noopSquelcher.shouldSquelch());

        assertThrows(UnsupportedOperationException.class, noopSquelcher::startSquelching);
        assertThrows(UnsupportedOperationException.class, noopSquelcher::stopSquelching);

        assertFalse(noopSquelcher.shouldSquelch());
    }
}
