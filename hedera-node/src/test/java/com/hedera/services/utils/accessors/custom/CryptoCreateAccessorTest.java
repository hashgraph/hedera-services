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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.KeyUtils;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoCreateAccessorTest {
    private static final Key adminKey = KeyUtils.A_THRESHOLD_KEY;
    private static final long autoRenewPeriod = 1_234_567L;
    private static final long now = 1_234_567L;
    private static final String memo = "Eternal sunshine of the spotless mind";

    @Mock private AccessorFactory accessorFactory;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private OptionValidator validator;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private NodeInfo nodeInfo;

    @Test
    void setCryptoCreateUsageMetaWorks() {
        final var txn = signedCryptoCreateTxn();
        final var accessor = getAccessor(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoCreateMeta(accessor);

        assertEquals(137, expandedMeta.getBaseSize());
        assertEquals(autoRenewPeriod, expandedMeta.getLifeTime());
        assertEquals(10, expandedMeta.getMaxAutomaticAssociations());
    }

    private SignedTxnAccessor getAccessor(final Transaction txn) {
        try {
            willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(any());
            return accessorFactory.constructSpecializedAccessor(txn.toByteArray());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void allGettersWork() throws InvalidProtocolBufferException {
        final var op =
                CryptoCreateTransactionBody.newBuilder()
                        .setMemo(memo)
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
                        .setKey(adminKey)
                        .setInitialBalance(100)
                        .setDeclineReward(true)
                        .setMaxAutomaticTokenAssociations(10)
                        .build();
        final var txn =
                buildTransactionFrom(
                        TransactionBody.newBuilder()
                                .setTransactionID(
                                        TransactionID.newBuilder()
                                                .setTransactionValidStart(
                                                        Timestamp.newBuilder().setSeconds(now)))
                                .setCryptoCreateAccount(op)
                                .build());
        given(validator.memoCheck(anyString())).willReturn(OK);
        given(validator.hasGoodEncoding(any())).willReturn(true);
        given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
        given(dynamicProperties.areTokenAssociationsLimited()).willReturn(false);
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var subject =
                new CryptoCreateAccessor(
                        txn.toByteArray(),
                        txn,
                        dynamicProperties,
                        validator,
                        () -> accounts,
                        nodeInfo);

        assertTrue(subject.supportsPrecheck());
        assertEquals(OK, subject.doPrecheck());
        assertEquals(memo, subject.memo());
        assertEquals(100, subject.initialBalance());
        assertEquals(autoRenewPeriod, subject.autoRenewPeriod().getSeconds());
        assertEquals(adminKey, subject.key());
        assertEquals(10, subject.maxTokenAssociations());
    }

    private Transaction signedCryptoCreateTxn() {
        return buildTransactionFrom(cryptoCreateOp());
    }

    private TransactionBody cryptoCreateOp() {
        final var op =
                CryptoCreateTransactionBody.newBuilder()
                        .setMemo(memo)
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
                        .setKey(adminKey)
                        .setMaxAutomaticTokenAssociations(10);
        return TransactionBody.newBuilder()
                .setTransactionID(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setCryptoCreateAccount(op)
                .build();
    }
}
