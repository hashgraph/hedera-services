/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.network;

import com.swirlds.platform.Connection;
import com.swirlds.platform.network.NetworkUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class NetworkUtilsTest {
    @Test
    void handleNetworkExceptionTest() {
        final Connection c = new FakeConnection();
        Assertions.assertDoesNotThrow(
                () -> NetworkUtils.handleNetworkException(new Exception(), c),
                "handling should not throw an exception");
        Assertions.assertFalse(c.connected(), "method should have disconnected the connection");

        Assertions.assertThrows(
                InterruptedException.class,
                () -> NetworkUtils.handleNetworkException(new InterruptedException(), null),
                "an interrupted exception should be rethrown");
    }
}
