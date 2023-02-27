/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging.payloads.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.logging.payloads.ReconnectPeerInfoPayload;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReconnectPeerInfoPayloadTest {

    private static Stream<Arguments> peerInfo() {
        return Stream.of(Arguments.of(0L, null), Arguments.of(1L, ""), Arguments.of(2L, "hello swirlds"));
    }

    private ReconnectPeerInfoPayload payload;

    @BeforeEach
    public void setup() {
        payload = new ReconnectPeerInfoPayload();
    }

    @Test
    void testConstructor() {
        assertNotNull(payload.getMessage(), "message should be initialized.");
        assertTrue(payload.getMessage().isEmpty(), "message should be initialized to an empty string.");
        assertNotNull(payload.getPeerInfo(), "peerInfo should be initialized.");
        assertTrue(payload.getPeerInfo().isEmpty(), "peerInfo should be initialized to an empty collection.");
    }

    @ParameterizedTest
    @MethodSource("peerInfo")
    void testAddPeerInfo(final long peerId, final String msg) {
        payload.addPeerInfo(peerId, msg);
        assertFalse(payload.getPeerInfo().isEmpty(), "peerInfo should not be empty after adding a value.");
        assertEquals(
                1,
                payload.getPeerInfo().size(),
                "peerInfo size should be equal to the number of times 'addPeerInfo' is called.");

        ReconnectPeerInfoPayload.PeerInfo info = payload.getPeerInfo().get(0);
        assertEquals(peerId, info.getNode(), "peerInfo left value should match the value provided.");
        assertEquals(msg, info.getMessage(), "peerInfo right value should match the value provided.");
    }
}
