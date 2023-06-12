/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system.platformstatus.statuslogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.system.platformstatus.PlatformStatus;
import com.swirlds.common.system.platformstatus.PlatformStatusAction;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Utility methods for testing {@link PlatformStatusLogic} implementations.
 */
public class StatusLogicTestUtils {
    /**
     * Hidden constructor
     */
    private StatusLogicTestUtils() {}

    /**
     * Trigger an action and assert that the new status is as expected.
     *
     * @param logic          the logic to test
     * @param action         the action to trigger
     * @param expectedStatus the expected status after the action is triggered
     */
    public static void triggerActionAndAssertTransition(
            @NonNull final PlatformStatusLogic logic,
            @NonNull final PlatformStatusAction action,
            @NonNull final PlatformStatus expectedStatus) {

        final PlatformStatus newStatus = logic.processStatusAction(action);
        assertEquals(expectedStatus, newStatus);
    }

    /**
     * Trigger an action and assert that the status does not change.
     *
     * @param logic  the logic to test
     * @param action the action to trigger
     */
    public static void triggerActionAndAssertNoTransition(
            @NonNull final PlatformStatusLogic logic, @NonNull final PlatformStatusAction action) {

        final PlatformStatus originalStatus = logic.getStatus();
        final PlatformStatus newStatus = logic.processStatusAction(action);

        assertEquals(originalStatus, newStatus);
    }

    /**
     * Trigger an action and assert that an exception is thrown.
     *
     * @param logic  the logic to test
     * @param action the action to trigger
     */
    public static void triggerActionAndAssertException(
            @NonNull final PlatformStatusLogic logic, @NonNull final PlatformStatusAction action) {

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    logic.processStatusAction(action);
                },
                "Expected an exception to be thrown when triggering action " + action + " in status "
                        + logic.getStatus());
    }
}
