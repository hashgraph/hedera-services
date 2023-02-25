/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.exceptions;

import static com.swirlds.platform.exceptions.ExceptionAssertions.CAUSE;
import static com.swirlds.platform.exceptions.ExceptionAssertions.CAUSE_MESSAGE;
import static com.swirlds.platform.exceptions.ExceptionAssertions.MESSAGE;
import static com.swirlds.platform.exceptions.ExceptionAssertions.assertExceptionContains;
import static com.swirlds.platform.exceptions.ExceptionAssertions.assertExceptionSame;

import com.swirlds.platform.chatter.protocol.PeerMessageException;
import com.swirlds.platform.crypto.KeyCertPurpose;
import com.swirlds.platform.crypto.KeyGeneratingException;
import com.swirlds.platform.crypto.KeyLoadingException;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.sync.SyncTimeoutException;
import com.swirlds.platform.system.PlatformConstructionException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExceptionTests {

    @Test
    void testKeyGeneratingException() {
        assertExceptionSame(new KeyGeneratingException(MESSAGE, CAUSE), MESSAGE, CAUSE);
    }

    @Test
    void testKeyLoadingException() {
        assertExceptionSame(new KeyLoadingException(MESSAGE), MESSAGE, null);
        assertExceptionSame(new KeyLoadingException(MESSAGE, CAUSE), MESSAGE, CAUSE);
        assertExceptionSame(new KeyLoadingException(MESSAGE, CAUSE), MESSAGE, CAUSE);
        final String name = "a name";
        assertExceptionContains(
                new KeyLoadingException(MESSAGE, KeyCertPurpose.SIGNING, name), List.of(name, MESSAGE), null);
    }

    @Test
    void testSyncTimeoutException() {
        assertExceptionContains(
                new SyncTimeoutException(Duration.ofSeconds(61), Duration.ofSeconds(60)),
                List.of("sync time exceeded", "60 sec", "61 sec"),
                null);
    }

    @Test
    void testPlatformConstructionException() {
        assertExceptionSame(new PlatformConstructionException(MESSAGE, CAUSE), MESSAGE, CAUSE);
        assertExceptionContains(new PlatformConstructionException(CAUSE), List.of(CAUSE_MESSAGE), CAUSE);
    }

    @Test
    void testNetworkProtocolException() {
        assertExceptionSame(new NetworkProtocolException(MESSAGE), MESSAGE, null);
        assertExceptionContains(new NetworkProtocolException(CAUSE), List.of(CAUSE_MESSAGE), CAUSE);
    }

    @Test
    void testPeerMessageException() {
        assertExceptionSame(new PeerMessageException(MESSAGE), MESSAGE, null);
    }
}
