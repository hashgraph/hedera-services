/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.utility.throttle.RateLimiter;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkUtils;
import java.time.Duration;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class NetworkUtilsTest {
    private final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
    private final PlatformContext platformContext =
            TestPlatformContextBuilder.create().withConfiguration(configuration).build();
    private final RateLimiter socketExceptionRateLimiter =
            new RateLimiter(platformContext.getTime(), Duration.ofMinutes(1));

    @Test
    void handleNetworkExceptionTest() {
        final Connection c = new FakeConnection();
        Assertions.assertDoesNotThrow(
                () -> NetworkUtils.handleNetworkException(new Exception(), c, socketExceptionRateLimiter),
                "handling should not throw an exception");
        Assertions.assertFalse(c.connected(), "method should have disconnected the connection");

        Assertions.assertDoesNotThrow(
                () -> NetworkUtils.handleNetworkException(
                        new SSLException("test", new NullPointerException()), null, socketExceptionRateLimiter),
                "handling should not throw an exception");

        Assertions.assertThrows(
                InterruptedException.class,
                () -> NetworkUtils.handleNetworkException(new InterruptedException(), null, socketExceptionRateLimiter),
                "an interrupted exception should be rethrown");
    }
}
