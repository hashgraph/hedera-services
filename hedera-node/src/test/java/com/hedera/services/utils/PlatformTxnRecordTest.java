package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */


import com.google.protobuf.InvalidProtocolBufferException;
import com.swirlds.common.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.utils.SleepingPause.SLEEPING_PAUSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

public class PlatformTxnRecordTest {
    private Transaction platformTxn;
    private PlatformTxnRecord subject;

    @BeforeEach
    private void setup(){
        platformTxn = mock(Transaction.class);
        given(platformTxn.getContents()).willReturn(
                com.hederahashgraph.api.proto.java.Transaction.getDefaultInstance().toByteArray());

        subject = new PlatformTxnRecord(1L);
    }

    @Test
    public void getPlatformTxnAccessorWillReturnNullWhenEmptyTransaction() {
        // when
        assertNull(subject.getPlatformTxnAccessor(null));
    }

    @Test
    void getPlatformTxnAccessorWillReturnNullWhenTransactionsAreDifferent() throws InvalidProtocolBufferException{
        // setup
        Transaction transactionA = new Transaction("abc".getBytes());

        // when
        subject.addTransaction(platformTxn);

        // then
        assertNull(subject.getPlatformTxnAccessor(transactionA));
    }

    @Test
    void addTransactionWillThrowExceptionOnInvalidTxn() {
        // setup
        Transaction transactionA = new Transaction("abc".getBytes());

        // when
        assertThrows(InvalidProtocolBufferException.class, () -> subject.addTransaction(transactionA));
        assertThrows(NullPointerException.class, () -> subject.addTransaction(null));
    }

    @Test
    void getPlatformTxnAccessorReturnsSameTransaction() throws InvalidProtocolBufferException{
        // when
        subject.addTransaction(platformTxn);

        //then
        assertEquals(platformTxn, subject.getPlatformTxnAccessor(platformTxn).getPlatformTxn());
    }

    @Test
    void defaultTimeToExpireIsSame(){
        // when
        assertEquals(1, subject.getTimeToExpire());
    }

    @Test
    void getPlatformTxnAccessorWillReturnNullAfterExpiry() throws InvalidProtocolBufferException {
        // when
        subject.addTransaction(platformTxn);

        // then
        SLEEPING_PAUSE.forMs(50L);
        assertEquals(platformTxn, subject.getPlatformTxnAccessor(platformTxn).getPlatformTxn());
        SLEEPING_PAUSE.forMs(1000L);
        assertNull(subject.getPlatformTxnAccessor(platformTxn));
    }
}
