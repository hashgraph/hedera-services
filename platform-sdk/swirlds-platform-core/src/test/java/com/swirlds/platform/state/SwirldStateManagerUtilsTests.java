// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.state;

import static com.swirlds.platform.test.fixtures.state.FakeMerkleStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.swirlds.platform.metrics.SwirldStateMetrics;
import com.swirlds.platform.system.BasicSoftwareVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SwirldStateManagerUtilsTests {

    @BeforeEach
    void setup() {}

    @Test
    void testFastCopyIsMutable() {

        final PlatformMerkleStateRoot state = new PlatformMerkleStateRoot(
                FAKE_MERKLE_STATE_LIFECYCLES, version -> new BasicSoftwareVersion(version.major()));
        FAKE_MERKLE_STATE_LIFECYCLES.initPlatformState(state);
        state.reserve();
        final SwirldStateMetrics stats = mock(SwirldStateMetrics.class);
        final PlatformMerkleStateRoot result =
                SwirldStateManagerUtils.fastCopy(state, stats, new BasicSoftwareVersion(1));

        assertFalse(result.isImmutable(), "The copy state should be mutable.");
        assertEquals(
                1,
                state.getReservationCount(),
                "Fast copy should not change the reference count of the state it copies.");
        assertEquals(
                1, result.getReservationCount(), "Fast copy should return a new state with a reference count of 1.");
    }
}
