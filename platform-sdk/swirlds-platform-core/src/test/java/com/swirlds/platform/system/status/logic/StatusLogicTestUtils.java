/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.status.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.PlatformStatus;
import com.swirlds.platform.system.status.IllegalPlatformStatusException;
import com.swirlds.platform.system.status.actions.PlatformStatusAction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;

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
     * @param actionHandler  the logic method to test
     * @param action         the action to trigger
     * @param expectedStatus the expected status after the action is triggered
     */
    public static <T extends PlatformStatusAction> void triggerActionAndAssertTransition(
            @NonNull final Function<T, PlatformStatusLogic> actionHandler,
            @NonNull final T action,
            @NonNull final PlatformStatus expectedStatus) {

        final PlatformStatus newStatus = actionHandler.apply(action).getStatus();
        assertEquals(expectedStatus, newStatus);
    }

    /**
     * Trigger an action and assert that the status does not change.
     *
     * @param actionHandler  the logic method to test
     * @param action         the action to trigger
     * @param originalStatus the original status before the action is triggered
     */
    public static <T extends PlatformStatusAction> void triggerActionAndAssertNoTransition(
            @NonNull final Function<T, PlatformStatusLogic> actionHandler,
            @NonNull final T action,
            @NonNull final PlatformStatus originalStatus) {

        final PlatformStatus newStatus = actionHandler.apply(action).getStatus();

        assertEquals(originalStatus, newStatus);
    }

    /**
     * Trigger an action and assert that an exception is thrown.
     *
     * @param actionHandler  the logic method to test
     * @param action         the action to trigger
     * @param originalStatus the original status before the action is triggered
     */
    public static <T extends PlatformStatusAction> void triggerActionAndAssertException(
            @NonNull final Function<T, PlatformStatusLogic> actionHandler,
            @NonNull final T action,
            @NonNull final PlatformStatus originalStatus) {

        assertThrows(
                IllegalPlatformStatusException.class,
                () -> actionHandler.apply(action),
                "Expected an exception to be thrown when triggering action " + action + " in status " + originalStatus);
    }
}
