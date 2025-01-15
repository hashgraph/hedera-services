/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.component.framework.schedulers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.platform.component.framework.schedulers.internal.DefaultSquelcher;
import com.swirlds.platform.component.framework.schedulers.internal.Squelcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link DefaultSquelcher} class.
 */
class DefaultSquelcherTests {
    @Test
    @DisplayName("Standard operation")
    void standardOperation() {
        final Squelcher defaultSquelcher = new DefaultSquelcher();
        assertFalse(defaultSquelcher.shouldSquelch());

        defaultSquelcher.startSquelching();
        assertTrue(defaultSquelcher.shouldSquelch());

        defaultSquelcher.stopSquelching();
        assertFalse(defaultSquelcher.shouldSquelch());
    }

    @Test
    @DisplayName("Illegal state handling")
    void illegalStateHandling() {
        final Squelcher defaultSquelcher = new DefaultSquelcher();
        assertThrows(IllegalStateException.class, defaultSquelcher::stopSquelching);

        defaultSquelcher.startSquelching();
        assertThrows(IllegalStateException.class, defaultSquelcher::startSquelching);
    }
}
