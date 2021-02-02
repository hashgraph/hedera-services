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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asSchedule;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TriggeredTxnAccessorTest {
    AccountID id = asAccount("0.0.1001");
    String nonce = "123";
    boolean scheduled = true;
    AccountID payer = asAccount("0.0.1234");
    ScheduleID scheduleRef = asSchedule("0.0.5876");

    TransactionID txnId = TransactionID.newBuilder()
            .setAccountID(id)
            .setNonce(ByteString.copyFromUtf8(nonce))
            .setScheduled(scheduled).build();
    TransactionBody txnBody = TransactionBody.newBuilder()
            .setTransactionID(txnId)
            .build();
    SignedTransaction signedTxn = SignedTransaction.newBuilder()
            .setBodyBytes(txnBody.toByteString())
            .build();
    Transaction tx = Transaction.newBuilder()
            .setSignedTransactionBytes(
                    signedTxn.toByteString())
            .build();

    private TriggeredTxnAccessor subject;

    @BeforeEach
    public void setup() throws InvalidProtocolBufferException {
        subject = new TriggeredTxnAccessor(tx.toByteArray(), payer, scheduleRef);
    }

    @Test
    public void validProperties() {
        assertEquals(tx, subject.getBackwardCompatibleSignedTxn());
        assertEquals(tx, subject.getSignedTxn4Log());
        assertArrayEquals(tx.toByteArray(), subject.getBackwardCompatibleSignedTxnBytes());
        assertEquals(scheduleRef, subject.getScheduleRef());
        assertEquals(payer, subject.getPayer());
        assertEquals(txnBody, subject.getTxn());
        assertArrayEquals(txnBody.toByteArray(), subject.getTxnBytes());
        assertEquals(txnId, subject.getTxnId());
        assertArrayEquals(CommonUtils.noThrowSha384HashOf(signedTxn.toByteArray()),
                subject.getHash().toByteArray());
    }
}
