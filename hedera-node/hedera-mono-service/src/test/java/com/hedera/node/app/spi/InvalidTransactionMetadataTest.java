/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.node.app.spi.meta.impl.InvalidTransactionMetadata;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvalidTransactionMetadataTest {
    private InvalidTransactionMetadata subject;

    @Test
    void returnsFailureMetadata() {
        final var txn = invalidCreateAccountTransaction();
        subject = new InvalidTransactionMetadata(txn, INVALID_TRANSACTION_BODY);

        assertTrue(subject.failed());
        assertEquals(INVALID_TRANSACTION_BODY, subject.failureStatus());
        assertEquals(txn, subject.getTxn());
        assertEquals(Collections.emptyList(), subject.getReqKeys());
    }

    private Transaction invalidCreateAccountTransaction() {
        return Transaction.newBuilder()
                .setSignedTransactionBytes(
                        SignedTransaction.newBuilder()
                                .setBodyBytes(ByteString.copyFromUtf8("NONSENSE"))
                                .build()
                                .toByteString())
                .build();
    }
}
