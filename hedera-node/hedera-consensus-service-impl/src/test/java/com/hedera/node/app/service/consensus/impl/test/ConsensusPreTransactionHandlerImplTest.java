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
package com.hedera.node.app.service.consensus.impl.test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.consensus.impl.ConsensusPreTransactionHandlerImpl;
import com.hedera.node.app.service.mono.Utils;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusPreTransactionHandlerImplTest {
    private static final AccountID ACCOUNT_ID_3 = IdUtils.asAccount("0.0.3");
    private static final Key SIMPLE_KEY_A =
            Key.newBuilder()
                    .setEd25519(ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()))
                    .build();
    private static final Key SIMPLE_KEY_B =
            Key.newBuilder()
                    .setEd25519(ByteString.copyFrom("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes()))
                    .build();

    @Mock private AccountKeyLookup keyFinder;

    private ConsensusPreTransactionHandlerImpl subject;

    @BeforeEach
    void setUp() {
        subject = new ConsensusPreTransactionHandlerImpl(keyFinder);
    }

    @Test
    void createAddsAllNonNullKeyInputs() {
        final var payerKey = mockPayerLookup();
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;

        final var result =
                subject.preHandleCreateTopic(newCreateTxn(adminKey, submitKey), ACCOUNT_ID_3);

        assertOkResponse(result);
        Assertions.assertEquals(payerKey, result.payerKey());
        final var expectedHederaAdminKey = Utils.asHederaKey(adminKey).orElseThrow();
        Assertions.assertTrue(result.requiredNonPayerKeys().contains(expectedHederaAdminKey));
        final var expectedHederaSubmitKey = Utils.asHederaKey(submitKey).orElseThrow();
        Assertions.assertTrue(result.requiredNonPayerKeys().contains(expectedHederaSubmitKey));
        Assertions.assertEquals(2, result.requiredNonPayerKeys().size());
    }

    @Test
    void createOnlyRequiresPayerKey() {
        final var payerKey = mockPayerLookup();

        final var result = subject.preHandleCreateTopic(newCreateTxn(null, null), ACCOUNT_ID_3);

        assertOkResponse(result);
        Assertions.assertEquals(payerKey, result.payerKey());
        Assertions.assertEquals(List.of(), result.requiredNonPayerKeys());
    }

    @Test
    void createAddsDifferentAdminKey() {
        final var payerKey = mockPayerLookup();
        final var adminKey = SIMPLE_KEY_A;

        final var result = subject.preHandleCreateTopic(newCreateTxn(adminKey, null), ACCOUNT_ID_3);

        assertOkResponse(result);
        Assertions.assertEquals(payerKey, result.payerKey());
        final var expectedHederaAdminKey = Utils.asHederaKey(adminKey).orElseThrow();
        Assertions.assertTrue(result.requiredNonPayerKeys().contains(expectedHederaAdminKey));
        Assertions.assertEquals(1, result.requiredNonPayerKeys().size());
    }

    @Test
    void createAddsDifferentSubmitKey() {
        final var payerKey = mockPayerLookup();
        final var submitKey = SIMPLE_KEY_B;

        final var result =
                subject.preHandleCreateTopic(newCreateTxn(null, submitKey), ACCOUNT_ID_3);

        assertOkResponse(result);
        Assertions.assertEquals(payerKey, result.payerKey());
        final var expectedHederaSubmitKey = Utils.asHederaKey(submitKey).orElseThrow();
        Assertions.assertTrue(result.requiredNonPayerKeys().contains(expectedHederaSubmitKey));
        Assertions.assertEquals(1, result.requiredNonPayerKeys().size());
    }

    @Test
    void createAddsPayerAsAdmin() {
        final var protoPayerKey = SIMPLE_KEY_A;
        final var payerKey = mockPayerLookup(protoPayerKey);

        final var result =
                subject.preHandleCreateTopic(newCreateTxn(protoPayerKey, null), ACCOUNT_ID_3);

        assertOkResponse(result);
        Assertions.assertEquals(payerKey, result.payerKey());
        Assertions.assertEquals(List.of(payerKey), result.requiredNonPayerKeys());
    }

    @Test
    void createAddsPayerAsSubmitter() {
        final var protoPayerKey = SIMPLE_KEY_B;
        final var payerKey = mockPayerLookup(protoPayerKey);

        final var result =
                subject.preHandleCreateTopic(newCreateTxn(null, protoPayerKey), ACCOUNT_ID_3);

        assertOkResponse(result);
        Assertions.assertEquals(payerKey, result.payerKey());
        Assertions.assertEquals(List.of(payerKey), result.requiredNonPayerKeys());
    }

    @Test
    void createFailsWhenPayerNotFound() {
        given(keyFinder.getKey(any()))
                .willReturn(
                        KeyOrLookupFailureReason.withFailureReason(
                                ResponseCodeEnum
                                        .ACCOUNT_ID_DOES_NOT_EXIST)); // Any error response code
        final var inputTxn = newCreateTxn(null, null);

        final var result = subject.preHandleCreateTopic(inputTxn, ACCOUNT_ID_3);

        Assertions.assertEquals(ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID, result.status());
        Assertions.assertNull(result.payerKey());
        Assertions.assertTrue(result.failed());
        Assertions.assertTrue(result.requiredNonPayerKeys().isEmpty());
    }

    @Test
    void notImplementedMethodsThrowException() {
        assertThrows(
                NotImplementedException.class,
                () -> subject.preHandleUpdateTopic(mock(TransactionBody.class), ACCOUNT_ID_3));
        assertThrows(
                NotImplementedException.class,
                () -> subject.preHandleDeleteTopic(mock(TransactionBody.class), ACCOUNT_ID_3));
        assertThrows(
                NotImplementedException.class,
                () -> subject.preHandleSubmitMessage(mock(TransactionBody.class), ACCOUNT_ID_3));
    }

    private HederaKey mockPayerLookup() {
        return mockPayerLookup(KeyUtils.A_COMPLEX_KEY);
    }

    private HederaKey mockPayerLookup(Key key) {
        final var returnKey = Utils.asHederaKey(key).orElseThrow();
        given(keyFinder.getKey(ACCOUNT_ID_3))
                .willReturn(KeyOrLookupFailureReason.withKey(returnKey));
        return returnKey;
    }

    private static TransactionBody newCreateTxn(Key adminKey, Key submitKey) {
        final var txnId = TransactionID.newBuilder().setAccountID(ACCOUNT_ID_3).build();
        final var createTopicBuilder = ConsensusCreateTopicTransactionBody.newBuilder();
        if (adminKey != null) {
            createTopicBuilder.setAdminKey(adminKey);
        }
        if (submitKey != null) {
            createTopicBuilder.setSubmitKey(submitKey);
        }
        return TransactionBody.newBuilder()
                .setTransactionID(txnId)
                .setConsensusCreateTopic(createTopicBuilder.build())
                .build();
    }

    private static void assertOkResponse(TransactionMetadata result) {
        Assertions.assertEquals(ResponseCodeEnum.OK, result.status());
        Assertions.assertFalse(result.failed());
    }
}
