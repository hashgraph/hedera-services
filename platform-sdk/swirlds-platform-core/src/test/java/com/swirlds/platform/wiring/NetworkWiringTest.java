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

package com.swirlds.platform.wiring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.network.connectivity.ConnectionServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NetworkWiringTest {
    @Test
    @DisplayName("Assert that all Network input wires are bound to something")
    void testBindings() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final NetworkWiring wiring = new NetworkWiring(platformContext);

        wiring.bind(mock(ConnectionServer.class));
        assertFalse(wiring.getModel().checkForUnboundInputWires());
    }
}
