/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils.accessors.custom;

import static com.hedera.services.utils.accessors.SignedTxnAccessorTest.buildTransactionFrom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willCallRealMethod;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SubmitMessageAccessorTest {
    @Mock private AccessorFactory accessorFactory;

    @Test
    void understandsSubmitMessageMeta() {
        final var message = "And after, arranged it in a song";
        final var txnBody =
                TransactionBody.newBuilder()
                        .setConsensusSubmitMessage(
                                ConsensusSubmitMessageTransactionBody.newBuilder()
                                        .setMessage(ByteString.copyFromUtf8(message)))
                        .build();
        final var txn = buildTransactionFrom(txnBody);
        final var subject = getAccessor(txn);

        final var submitMeta = subject.getSpanMapAccessor().getSubmitMessageMeta(subject);

        assertEquals(message.length(), submitMeta.numMsgBytes());
    }

    private SignedTxnAccessor getAccessor(final Transaction txn) {
        try {
            willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(any());
            return accessorFactory.constructSpecializedAccessor(txn.toByteArray());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }
    }
}
