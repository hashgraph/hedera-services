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
package com.hedera.node.app.service.consensus;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.Utils;
import com.hedera.node.app.service.consensus.impl.ConsensusPreTransactionHandlerImpl;
import com.hedera.node.app.service.token.impl.AccountStore;
import com.hedera.node.app.service.token.impl.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.services.utils.KeyUtils;
import com.hedera.test.utils.IdUtils;
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

    @Mock private AccountStore accountStore;

    private ConsensusPreTransactionHandlerImpl subject;

    @BeforeEach
    void setUp() {
        subject = new ConsensusPreTransactionHandlerImpl(accountStore);
    }

    @Test
    void createAddsAllNonNullKeyInputs() {
        final var payerKey = mockPayerLookup();
        final var adminKey = KeyUtils.B_COMPLEX_KEY;
        final var submitKey = KeyUtils.C_COMPLEX_KEY;

        final var result = subject.preHandleCreateTopic(newCreateTxn(adminKey, submitKey));

        assertOkResponse(result);
        Assertions.assertTrue(result.getReqKeys().contains(payerKey));
        final var expectedHederaAdminKey = Utils.asHederaKey(adminKey).orElseThrow();
        Assertions.assertTrue(result.getReqKeys().contains(expectedHederaAdminKey));
        final var expectedHederaSubmitKey = Utils.asHederaKey(submitKey).orElseThrow();
        Assertions.assertTrue(result.getReqKeys().contains(expectedHederaSubmitKey));
        Assertions.assertEquals(3, result.getReqKeys().size());
    }

    @Test
    void createOnlyRequiresPayerKey() {
        final var payerKey = mockPayerLookup();

        final var result = subject.preHandleCreateTopic(newCreateTxn(null, null));

        assertOkResponse(result);
        Assertions.assertEquals(List.of(payerKey), result.getReqKeys());
    }

    @Test
    void createAddsDifferentAdminKey() {
        final var payerKey = mockPayerLookup();
        final var adminKey = KeyUtils.B_COMPLEX_KEY;

        final var result = subject.preHandleCreateTopic(newCreateTxn(adminKey, null));

        assertOkResponse(result);
        Assertions.assertTrue(result.getReqKeys().contains(payerKey));
        final var expectedHederaAdminKey = Utils.asHederaKey(adminKey).orElseThrow();
        Assertions.assertTrue(result.getReqKeys().contains(expectedHederaAdminKey));
        Assertions.assertEquals(2, result.getReqKeys().size());
    }

    @Test
    void createAddsDifferentSubmitKey() {
        final var payerKey = mockPayerLookup();
        final var submitKey = KeyUtils.B_COMPLEX_KEY;

        final var result = subject.preHandleCreateTopic(newCreateTxn(null, submitKey));

        assertOkResponse(result);
        Assertions.assertTrue(result.getReqKeys().contains(payerKey));
        final var expectedHederaSubmitKey = Utils.asHederaKey(submitKey).orElseThrow();
        Assertions.assertTrue(result.getReqKeys().contains(expectedHederaSubmitKey));
        Assertions.assertEquals(2, result.getReqKeys().size());
    }

    @Test
    void createAddsPayerAsAdmin() throws InvalidProtocolBufferException {
        final var payerKey = mockPayerLookup();
        final var protoPayerKey = Key.parseFrom(KeyUtils.A_COMPLEX_KEY.toByteArray());

        final var result = subject.preHandleCreateTopic(newCreateTxn(protoPayerKey, null));

        assertOkResponse(result);
        Assertions.assertEquals(List.of(payerKey), result.getReqKeys());
    }

    @Test
    void createAddsPayerAsSubmitter() throws InvalidProtocolBufferException {
        final var payerKey = mockPayerLookup();
        final var protoPayerKey = Key.parseFrom(KeyUtils.A_COMPLEX_KEY.toByteArray());

        final var result = subject.preHandleCreateTopic(newCreateTxn(null, protoPayerKey));

        assertOkResponse(result);
        Assertions.assertEquals(List.of(payerKey), result.getReqKeys());
    }

    @Test
    void createFailsWhenPayerNotFound() {
        given(accountStore.getKey(any()))
                .willReturn(
                        KeyOrLookupFailureReason.withFailureReason(
                                ResponseCodeEnum
                                        .ACCOUNT_ID_DOES_NOT_EXIST)); // Any error response code
        final var inputTxn = newCreateTxn(null, null);

        final var result = subject.preHandleCreateTopic(inputTxn);

        Assertions.assertEquals(ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID, result.status());
        Assertions.assertTrue(result.failed());
        Assertions.assertTrue(result.getReqKeys().isEmpty());
    }

    @Test
    void notImplementedMethodsThrowException() {
        assertThrows(
                NotImplementedException.class,
                () -> subject.preHandleUpdateTopic(mock(TransactionBody.class)));
        assertThrows(
                NotImplementedException.class,
                () -> subject.preHandleDeleteTopic(mock(TransactionBody.class)));
        assertThrows(
                NotImplementedException.class,
                () -> subject.preHandleSubmitMessage(mock(TransactionBody.class)));
    }

    private HederaKey mockPayerLookup() {
        final var returnKey = Utils.asHederaKey(KeyUtils.A_COMPLEX_KEY).orElseThrow();
        given(accountStore.getKey(ACCOUNT_ID_3))
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
