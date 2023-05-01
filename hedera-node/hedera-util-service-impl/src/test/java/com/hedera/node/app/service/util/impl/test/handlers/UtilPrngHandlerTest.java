/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.util.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PRNG_RANGE;
import static com.hedera.node.app.service.util.impl.handlers.UtilPrngHandler.MISSING_BYTES;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.util.UtilPrngTransactionBody;
import com.hedera.node.app.service.network.ReadableRunningHashLeafStore;
import com.hedera.node.app.service.util.impl.config.PrngConfig;
import com.hedera.node.app.service.util.impl.handlers.UtilPrngHandler;
import com.hedera.node.app.service.util.impl.records.UtilPrngRecordBuilder;
import com.hedera.node.app.service.util.records.PrngRecordBuilder;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;

import com.swirlds.common.threading.futures.StandardFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

@ExtendWith(MockitoExtension.class)
class UtilPrngHandlerTest {
    @Mock
    private PreHandleContext preHandleContext;
    @Mock
    private HandleContext handleContext;
    @Mock
    private ReadableRunningHashLeafStore readableRunningHashLeafStore;
    @Mock
    private RunningHash invalidRunningHash;
    private UtilPrngHandler subject;
    private UtilPrngTransactionBody txn;
    private PrngRecordBuilder recordBuilder;
    private static final Hash hash = new Hash(randomUtf8Bytes(48));
    private static final RunningHash nMinusThreeHash = new RunningHash(hash);

    @BeforeEach
    void setUp() {
        subject = new UtilPrngHandler();
        recordBuilder = new UtilPrngRecordBuilder();
        givenTxnWithoutRange();
    }
    @Test
    void preHandleValidatesRange()  {
        final var body = TransactionBody.newBuilder().utilPrng(UtilPrngTransactionBody.newBuilder()).build();
        given(preHandleContext.body()).willReturn(body);
        assertDoesNotThrow(() -> subject.preHandle(preHandleContext));
    }

    @Test
    void rejectsInvalidRange() {
        givenTxnWithRange(-10000);
        final var msg = assertThrows(PreCheckException.class, () -> subject.preHandle(preHandleContext));
        assertEquals(INVALID_PRNG_RANGE, msg.responseCode());
    }

    @Test
    void acceptsPositiveAndZeroRange() throws PreCheckException {
        givenTxnWithRange(10000);
        subject.preHandle(preHandleContext);

        givenTxnWithRange(0);
        subject.preHandle(preHandleContext);
    }

    @Test
    void acceptsNoRange() throws PreCheckException {
        givenTxnWithoutRange();
        subject.preHandle(preHandleContext);
    }

    @Test
    void returnsIfNotEnabled() {
        givenTxnWithoutRange();

        subject.handle(handleContext, txn, new PrngConfig(false), recordBuilder);

        assertFalse(recordBuilder.hasPrngNumber());
        assertEquals(0, recordBuilder.getPrngNumber());
    }

    @Test
    void followsHappyPathWithNoRange() {
        givenTxnWithoutRange();
        given(handleContext.createStore(ReadableRunningHashLeafStore.class)).willReturn(readableRunningHashLeafStore);
        given(readableRunningHashLeafStore.getNMinusThreeRunningHash()).willReturn(nMinusThreeHash);
        given(nMinusThreeHash.getFutureHash()).willReturn(new StandardFuture(hash.getValue()));

        subject.handle(handleContext, txn, new PrngConfig(true), recordBuilder);

        assertEquals(Bytes.wrap(hash.getValue()), recordBuilder.getPrngBytes());
        assertEquals(48, recordBuilder.getPrngBytes().length());
        assertNull(recordBuilder.getPrngNumber());
    }

    @Test
    void followsHappyPathWithRange() {
        givenTxnWithRange(20);
        given(handleContext.createStore(ReadableRunningHashLeafStore.class)).willReturn(readableRunningHashLeafStore);
        given(readableRunningHashLeafStore.getNMinusThreeRunningHash()).willReturn(nMinusThreeHash);
        given(nMinusThreeHash.getFutureHash()).willReturn(new StandardFuture(hash.getValue()));

        subject.handle(handleContext, txn, new PrngConfig(true), recordBuilder);

        final var num = recordBuilder.getPrngNumber();
        assertTrue(num >= 0 && num < 20);
        assertNull(recordBuilder.getPrngBytes());
    }

    @Test
    void followsHappyPathWithMaxIntegerRange() {
        givenTxnWithRange(Integer.MAX_VALUE);
        given(handleContext.createStore(ReadableRunningHashLeafStore.class)).willReturn(readableRunningHashLeafStore);
        given(readableRunningHashLeafStore.getNMinusThreeRunningHash()).willReturn(nMinusThreeHash);
        given(nMinusThreeHash.getFutureHash()).willReturn(new StandardFuture(hash.getValue()));

        subject.handle(handleContext, txn, new PrngConfig(true), recordBuilder);

        final var num = recordBuilder.getPrngNumber();
        assertTrue(num >= 0 && num < Integer.MAX_VALUE);
        assertNull(recordBuilder.getPrngBytes());
    }

    @Test
    void anyNegativeValueThrowsInPrecheck() {
        givenTxnWithRange(Integer.MIN_VALUE);

        final var msg = assertThrows(PreCheckException.class, () -> subject.preHandle(preHandleContext));
        assertEquals(INVALID_PRNG_RANGE, msg.responseCode());
    }

    @Test
    void givenRangeZeroGivesBitString() {
        givenTxnWithRange(0);
        given(handleContext.createStore(ReadableRunningHashLeafStore.class)).willReturn(readableRunningHashLeafStore);
        given(readableRunningHashLeafStore.getNMinusThreeRunningHash()).willReturn(nMinusThreeHash);
        given(nMinusThreeHash.getFutureHash()).willReturn(new StandardFuture(hash.getValue()));

        subject.handle(handleContext, txn, new PrngConfig(true), recordBuilder);

        assertEquals(Bytes.wrap(hash.getValue()), recordBuilder.getPrngBytes());
        assertEquals(48, recordBuilder.getPrngBytes().length());
        assertNull(recordBuilder.getPrngNumber());
    }

    @Test
    void nullHashesReturnNoRandomNumber()  {
        givenTxnWithRange(0);
        given(handleContext.createStore(ReadableRunningHashLeafStore.class)).willReturn(readableRunningHashLeafStore);
        given(readableRunningHashLeafStore.getNMinusThreeRunningHash()).willReturn(null);

        subject.handle(handleContext, txn, new PrngConfig(true), recordBuilder);

        assertEquals(Bytes.wrap(MISSING_BYTES), recordBuilder.getPrngBytes());
        assertEquals(48, recordBuilder.getPrngBytes().length());
        assertNull(recordBuilder.getPrngNumber());
    }

    @Test
    void interruptedWhileGettingHash()throws ExecutionException, InterruptedException  {
        givenTxnWithRange(0);
        given(handleContext.createStore(ReadableRunningHashLeafStore.class)).willReturn(readableRunningHashLeafStore);
        given(readableRunningHashLeafStore.getNMinusThreeRunningHash()).willReturn(invalidRunningHash);
        given(invalidRunningHash.getFutureHash().get()).willThrow(InterruptedException.class);

        assertThrows(IllegalStateException.class, () -> subject.handle(handleContext, txn, new PrngConfig(true), recordBuilder));
        
        assertNull(recordBuilder.getPrngBytes());
        assertNull(recordBuilder.getPrngNumber());
    }

    private void givenTxnWithRange(int range) {
        txn = UtilPrngTransactionBody.newBuilder().range(range).build();
    }

    private void givenTxnWithoutRange() {
        txn = UtilPrngTransactionBody.newBuilder().build();
    }

    private static byte[] randomUtf8Bytes(int n) {
        byte[] data = new byte[n];
        int i = 0;
        while (i < n) {
            byte[] rnd = UUID.randomUUID().toString().getBytes();
            System.arraycopy(rnd, 0, data, i, Math.min(rnd.length, n - 1 - i));
            i += rnd.length;
        }
        return data;
    }
}
