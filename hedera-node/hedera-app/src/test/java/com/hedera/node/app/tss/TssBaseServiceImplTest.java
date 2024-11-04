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

package com.hedera.node.app.tss;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.tss.schemas.V0560TssBaseSchema;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.state.spi.SchemaRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TssBaseServiceImplTest {
    private CountDownLatch latch;
    private final List<byte[]> receivedMessageHashes = new ArrayList<>();
    private final List<byte[]> receivedSignatures = new ArrayList<>();
    private final BiConsumer<byte[], byte[]> trackingConsumer = (a, b) -> {
        receivedMessageHashes.add(a);
        receivedSignatures.add(b);
        latch.countDown();
    };
    private final AtomicInteger numCalls = new AtomicInteger();
    private final BiConsumer<byte[], byte[]> secondConsumer = (a, b) -> numCalls.incrementAndGet();

    @Mock
    private SchemaRegistry registry;

    @Mock
    private AppContext.Gossip gossip;

    @Mock
    private AppContext appContext;

    @Mock
    private Metrics metrics;

    private TssBaseServiceImpl subject;

    @BeforeEach
    void setUp() {
        given(appContext.gossip()).willReturn(gossip);
        subject = new TssBaseServiceImpl(
                appContext,
                ForkJoinPool.commonPool(),
                ForkJoinPool.commonPool(),
                new PlaceholderTssLibrary(),
                ForkJoinPool.commonPool(),
                metrics);
    }

    @Test
    void onlyRegisteredConsumerReceiveCallbacks() throws InterruptedException {
        final var firstMessage = new byte[] {(byte) 0x01};
        final var secondMessage = new byte[] {(byte) 0x02};
        latch = new CountDownLatch(1);

        subject.registerLedgerSignatureConsumer(trackingConsumer);
        subject.registerLedgerSignatureConsumer(secondConsumer);

        subject.requestLedgerSignature(firstMessage);
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        subject.unregisterLedgerSignatureConsumer(secondConsumer);
        latch = new CountDownLatch(1);
        subject.requestLedgerSignature(secondMessage);
        assertTrue(latch.await(1, TimeUnit.SECONDS));

        assertEquals(2, receivedMessageHashes.size());
        assertEquals(2, receivedSignatures.size());
        assertArrayEquals(firstMessage, receivedMessageHashes.getFirst());
        assertArrayEquals(secondMessage, receivedMessageHashes.getLast());
        assertEquals(1, numCalls.get());
    }

    @Test
    void placeholderRegistersSchemas() {
        subject.registerSchemas(registry);
        verify(registry).register(argThat(s -> s instanceof V0560TssBaseSchema));
    }
}
