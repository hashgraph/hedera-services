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
package com.hedera.services.utils.accessors;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asModelId;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.TxnUtils.buildTransactionFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenWipeAccessorTest {
    @Mock private GlobalDynamicProperties properties;
    private TokenWipeAccessor subject;

    @Test
    void detectsUniqueTokenWipeSubtypeFromGrpcSyntax() throws InvalidProtocolBufferException {
        final var op =
                TokenWipeAccountTransactionBody.newBuilder()
                        .addAllSerialNumbers(List.of(1L, 2L, 3L))
                        .build();
        final var txn = buildTransactionFrom(TransactionBody.newBuilder().setTokenWipe(op).build());

        subject = new TokenWipeAccessor(txn.toByteArray(), txn, properties);

        assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, subject.getSubType());
    }

    @Test
    void detectsCommonTokenWipeSubtypeFromGrpcSyntax() throws InvalidProtocolBufferException {
        final var op = TokenWipeAccountTransactionBody.newBuilder().setAmount(1234L).build();
        final var txn = buildTransactionFrom(TransactionBody.newBuilder().setTokenWipe(op).build());

        subject = new TokenWipeAccessor(txn.toByteArray(), txn, properties);

        assertEquals(TOKEN_FUNGIBLE_COMMON, subject.getSubType());
    }

    @Test
    void gettersWorkAsExpected() throws InvalidProtocolBufferException {
        final var op =
                TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(asToken("0.0.123"))
                        .setAccount(asAccount("0.0.123456"))
                        .addAllSerialNumbers(List.of(1L, 2L, 3L))
                        .setAmount(1234L)
                        .build();
        final var txn = buildTransactionFrom(TransactionBody.newBuilder().setTokenWipe(op).build());
        given(properties.areNftsEnabled()).willReturn(true);

        subject = new TokenWipeAccessor(txn.toByteArray(), txn, properties);

        assertEquals(asModelId("0.0.123456"), subject.accountToWipe());
        assertEquals(asModelId("0.0.123"), subject.targetToken());
        assertEquals(List.of(1L, 2L, 3L), subject.serialNums());
        assertEquals(1234L, subject.amount());
        assertEquals(true, subject.supportsPrecheck());
        assertEquals(INVALID_TRANSACTION_BODY, subject.doPrecheck());
    }
}
