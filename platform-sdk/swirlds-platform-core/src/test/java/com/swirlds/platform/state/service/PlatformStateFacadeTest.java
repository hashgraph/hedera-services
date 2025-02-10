// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.platform.state.SwirldStateManagerUtils;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StateEventHandlerManagerFreezePeriodCheckerTest {

    @Test
    void isInFreezePeriodTest() {

        final Instant t1 = Instant.now();
        final Instant t2 = t1.plusSeconds(1);
        final Instant t3 = t2.plusSeconds(1);
        final Instant t4 = t3.plusSeconds(1);

        // No freeze time set
        assertFalse(SwirldStateManagerUtils.isInFreezePeriod(t1, null, null));

        // No freeze time set, previous freeze time set
        assertFalse(SwirldStateManagerUtils.isInFreezePeriod(t2, null, t1));

        // Freeze time is in the future, never frozen before
        assertFalse(SwirldStateManagerUtils.isInFreezePeriod(t2, t3, null));

        // Freeze time is in the future, frozen before
        assertFalse(SwirldStateManagerUtils.isInFreezePeriod(t2, t3, t1));

        // Freeze time is in the past, never frozen before
        assertTrue(SwirldStateManagerUtils.isInFreezePeriod(t2, t1, null));

        // Freeze time is in the past, frozen before at an earlier time
        assertTrue(SwirldStateManagerUtils.isInFreezePeriod(t3, t2, t1));

        // Freeze time in the past, already froze at that exact time
        assertFalse(SwirldStateManagerUtils.isInFreezePeriod(t3, t2, t2));
    }
}
