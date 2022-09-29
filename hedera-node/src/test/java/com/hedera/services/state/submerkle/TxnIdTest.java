/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.submerkle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;

class TxnIdTest {
    private static final int nonce = 123;
    private final AccountID payer = IdUtils.asAccount("0.0.75231");
    private final EntityId fcPayer = EntityId.fromGrpcAccountId(payer);
    private final Timestamp validStart =
            Timestamp.newBuilder().setSeconds(1_234_567L).setNanos(89).build();
    private final RichInstant fcValidStart = RichInstant.fromGrpc(validStart);

    private TxnId subject;

    @Test
    void gettersWork() {
        subject = scheduledSubject();

        assertEquals(fcPayer, subject.getPayerAccount());
        assertEquals(fcValidStart, subject.getValidStart());
    }

    @Test
    void changesWork() {
        final var newNonce = 666;
        subject = scheduledSubject();
        final var expectedNewNonce =
                TxnId.fromGrpc(base().setScheduled(true).setNonce(newNonce).build());
        final var expectedUnscheduledNewNonce =
                TxnId.fromGrpc(base().setScheduled(false).setNonce(newNonce).build());

        final var actualNewNonce = subject.withNonce(newNonce);
        final var actualUnscheduledNewNonce = subject.unscheduledWithNonce(newNonce);

        assertEquals(expectedNewNonce, actualNewNonce);
        assertEquals(expectedUnscheduledNewNonce, actualUnscheduledNewNonce);
    }

    @Test
    void sameAndNullEqualsWork() {
        subject = scheduledSubject();
        final var same = subject;
        assertEquals(subject, same);
        assertNotEquals(null, subject);
    }

    @Test
    void equalsWorks() {
        // given:
        subject = scheduledSubject();
        var subjectUserNonce = scheduledSubjectUserNonce();

        // expect:
        assertNotEquals(subject, unscheduledSubject());
        assertNotEquals(subject, subjectUserNonce);
    }

    @Test
    void hashCodeWorks() {
        // given:
        subject = scheduledSubject();

        // expect:
        assertNotEquals(subject.hashCode(), unscheduledSubject().hashCode());
    }

    @Test
    void toStringWorks() {
        // given:
        subject = scheduledSubject();
        // and:
        final var desired =
                "TxnId{payer=EntityId{shard=0, realm=0, num=75231}, validStart=RichInstant"
                        + "{seconds=1234567, nanos=89}, scheduled=true, nonce=123}";

        // expect:
        assertEquals(desired, subject.toString());
    }

    @Test
    void toGrpcWorks() {
        // given:
        var subject = scheduledSubject();
        // and:
        var expected = base().setScheduled(true).build();

        // expect:
        assertEquals(expected, subject.toGrpc());
    }

    @Test
    void merkleWorks() {
        // given:
        var subject = new TxnId();

        // expect:
        assertEquals(TxnId.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
        assertEquals(TxnId.RELEASE_0210_VERSION, subject.getVersion());
    }

    private TxnId unscheduledSubject() {
        return TxnId.fromGrpc(base().build());
    }

    private TxnId scheduledSubject() {
        return TxnId.fromGrpc(base().setScheduled(true).build());
    }

    private TxnId scheduledSubjectUserNonce() {
        return TxnId.fromGrpc(
                base().setScheduled(true).setNonce(TxnId.USER_TRANSACTION_NONCE).build());
    }

    private TransactionID.Builder base() {
        return TransactionID.newBuilder()
                .setAccountID(payer)
                .setNonce(nonce)
                .setTransactionValidStart(validStart);
    }
}
