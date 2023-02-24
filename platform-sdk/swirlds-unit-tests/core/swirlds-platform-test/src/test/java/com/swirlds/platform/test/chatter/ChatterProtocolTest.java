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

package com.swirlds.platform.test.chatter;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.mockito.Mockito.mock;

import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.platform.chatter.communication.ChatterProtocol;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.chatter.protocol.PeerMessageHandler;
import com.swirlds.platform.chatter.protocol.peer.CommunicationState;
import com.swirlds.platform.chatter.protocol.peer.PeerGossipState;
import com.swirlds.platform.chatter.protocol.peer.PeerInstance;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.test.network.communication.ReadWriteFakeConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChatterProtocolTest {
    private static Stream<Arguments> testExceptionThrownParams() {
        return Stream.of(
                Arguments.of(new IOException(), IOException.class),
                Arguments.of(new RuntimeException(), NetworkProtocolException.class));
    }

    @MethodSource("testExceptionThrownParams")
    @ParameterizedTest
    void testExceptionThrown(final Throwable exceptionToThrow, final Class<? extends Throwable> exceptionToCatch) {
        // create instances
        final PeerInstance peerInstance = new PeerInstance(
                new CommunicationState(),
                mock(PeerGossipState.class),
                mock(MessageProvider.class),
                mock(PeerMessageHandler.class));
        final ParallelExecutor parallelExecutor =
                new CachedPoolParallelExecutor(getStaticThreadManager(), "ChatterProtocolTest");
        final ChatterProtocol protocol = new ChatterProtocol(peerInstance, parallelExecutor);
        // throw exception on read
        final InputStream in = new InputStream() {
            @Override
            public int read() throws IOException {
                if (exceptionToThrow instanceof IOException ioe) {
                    throw ioe;
                } else if (exceptionToThrow instanceof RuntimeException re) {
                    throw re;
                }
                throw new Error("Unsupported exception type");
            }
        };
        final OutputStream out = mock(OutputStream.class);

        parallelExecutor.start();
        Assertions.assertThrows(exceptionToCatch, () -> protocol.runProtocol(new ReadWriteFakeConnection(in, out)));
    }
}
