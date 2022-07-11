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
package com.hedera.services.usage.token;

import static com.hedera.services.test.KeyUtils.A_KEY_LIST;
import static com.hedera.services.test.KeyUtils.C_COMPLEX_KEY;
import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.atMostOnce;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.google.protobuf.StringValue;
import com.hedera.services.test.IdUtils;
import com.hedera.services.test.KeyUtils;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenUpdateUsageTest {
    private long maxLifetime = 100 * 365 * 24 * 60 * 60L;

    private Key kycKey = KeyUtils.A_COMPLEX_KEY;
    private Key adminKey = KeyUtils.A_THRESHOLD_KEY;
    private Key freezeKey = KeyUtils.A_KEY_LIST;
    private Key supplyKey = KeyUtils.B_COMPLEX_KEY;
    private Key wipeKey = C_COMPLEX_KEY;
    private long oldExpiry = 2_345_670L;
    private long expiry = 2_345_678L;
    private long absurdExpiry = oldExpiry + 2 * maxLifetime;
    private long oldAutoRenewPeriod = 1_234_567L;
    private long now = oldExpiry - oldAutoRenewPeriod;
    private String oldSymbol = "ABC";
    private String symbol = "ABCDEFGH";
    private String oldName = "WhyWhy";
    private String name = "WhyWhyWhy";
    private String oldMemo = "Calm reigns";
    private String memo = "Calamity strikes";
    private int numSigs = 3, sigSize = 100, numPayerKeys = 1;
    private SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
    private AccountID treasury = IdUtils.asAccount("1.2.3");
    private AccountID autoRenewAccount = IdUtils.asAccount("3.2.1");
    private TokenID id = IdUtils.asToken("0.0.75231");

    private TokenUpdateTransactionBody op;
    private TransactionBody txn;

    private EstimatorFactory factory;
    private TxnUsageEstimator base;
    private TokenUpdateUsage subject;

    @BeforeEach
    void setUp() throws Exception {
        base = mock(TxnUsageEstimator.class);
        given(base.get()).willReturn(A_USAGES_MATRIX);

        factory = mock(EstimatorFactory.class);
        given(factory.get(any(), any(), any())).willReturn(base);

        TxnUsage.setEstimatorFactory(factory);
    }

    @Test
    void gettersWork() {
        assertEquals(TxnUsage.getEstimatorFactory(), factory);
    }

    @Test
    void createsExpectedCappedLifetimeDeltaForNewLargerKeys() {
        // setup:
        var curRb = curSize(A_KEY_LIST);
        var newRb = newRb();
        var expectedBytes = newRb + 3 * BASIC_ENTITY_ID_SIZE + 8;

        givenOp(absurdExpiry);
        // and:
        givenImpliedSubjectWithSmallerKeys();

        // when:
        var actual = subject.get();

        // then:
        assertEquals(A_USAGES_MATRIX, actual);
        // and:
        verify(base).addBpt(expectedBytes);
        verify(base).addRbs((newRb - curRb) * maxLifetime);
        verify(base)
                .addRbs(
                        TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 2, 0)
                                * USAGE_PROPERTIES.legacyReceiptStorageSecs());
    }

    @Test
    void createsExpectedDeltaForNewLargerKeys() {
        // setup:
        var curRb = curSize(A_KEY_LIST);
        var newRb = newRb();
        var expectedBytes = newRb + 3 * BASIC_ENTITY_ID_SIZE + 8;

        givenOp();
        // and:
        givenImpliedSubjectWithSmallerKeys();

        // when:
        var actual = subject.get();

        // then:
        assertEquals(A_USAGES_MATRIX, actual);
        // and:
        verify(base).addBpt(expectedBytes);
        verify(base).addRbs((newRb - curRb) * (expiry - now));
        verify(base)
                .addRbs(
                        TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 2, 0)
                                * USAGE_PROPERTIES.legacyReceiptStorageSecs());
    }

    @Test
    void createsExpectedDeltaForNewSmallerKeys() {
        // setup:
        var newRb = newRb();
        var expectedBytes = newRb + 3 * BASIC_ENTITY_ID_SIZE + 8;

        givenOp();
        // and:
        givenImpliedSubjectWithLargerKeys();

        // when:
        var actual = subject.get();

        // then:
        assertEquals(A_USAGES_MATRIX, actual);
        // and:
        verify(base).addBpt(expectedBytes);
        verify(base, atMostOnce()).addRbs(anyLong());
    }

    @Test
    void ignoresNewAutoRenewBytesIfAlreadyUsingAutoRenew() {
        // setup:
        var curRb = curSize(A_KEY_LIST) + BASIC_ENTITY_ID_SIZE;
        var newRb = newRb();
        var expectedBytes = newRb + 3 * BASIC_ENTITY_ID_SIZE + 8;

        givenOp();
        // and:
        givenImpliedSubjectWithSmallerKeys();
        subject.givenCurrentlyUsingAutoRenewAccount();

        // when:
        var actual = subject.get();

        // then:
        assertEquals(A_USAGES_MATRIX, actual);
        // and:
        verify(base).addBpt(expectedBytes);
        verify(base).addRbs((newRb - curRb) * (expiry - now));
        verify(base)
                .addRbs(
                        TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 2, 0)
                                * USAGE_PROPERTIES.legacyReceiptStorageSecs());
    }

    @Test
    void understandsRemovingAutoRenew() {
        // setup:
        var curRb = curSize(A_KEY_LIST) + BASIC_ENTITY_ID_SIZE;
        var newRb = newRb() - BASIC_ENTITY_ID_SIZE;
        var expectedBytes = newRb + 3 * BASIC_ENTITY_ID_SIZE + 8;

        givenOp();
        op = op.toBuilder().setAutoRenewAccount(AccountID.getDefaultInstance()).build();
        setTxn();
        // and:
        givenImpliedSubjectWithSmallerKeys();
        subject.givenCurrentlyUsingAutoRenewAccount();

        // when:
        var actual = subject.get();

        // then:
        assertEquals(A_USAGES_MATRIX, actual);
        // and:
        verify(base).addBpt(expectedBytes);
        verify(base).addRbs((newRb - curRb) * (expiry - now));
        verify(base)
                .addRbs(
                        TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 2, 0)
                                * USAGE_PROPERTIES.legacyReceiptStorageSecs());
    }

    private void givenImpliedSubjectWithLargerKeys() {
        givenImpliedSubjectWithKey(C_COMPLEX_KEY);
    }

    private void givenImpliedSubjectWithSmallerKeys() {
        givenImpliedSubjectWithKey(KeyUtils.A_KEY_LIST);
    }

    private void givenImpliedSubjectWithKey(Key oldKey) {
        givenImpliedSubjectWithExpiryAndKey(oldExpiry, oldKey);
    }

    private void givenImpliedSubjectWithExpiryAndKey(long extantExpiry, Key oldKey) {
        subject =
                TokenUpdateUsage.newEstimate(txn, sigUsage)
                        .givenCurrentExpiry(extantExpiry)
                        .givenCurrentMemo(oldMemo)
                        .givenCurrentName(oldName)
                        .givenCurrentSymbol(oldSymbol)
                        .givenCurrentAdminKey(Optional.of(oldKey))
                        .givenCurrentKycKey(Optional.of(oldKey))
                        .givenCurrentSupplyKey(Optional.of(oldKey))
                        .givenCurrentWipeKey(Optional.of(oldKey))
                        .givenCurrentFreezeKey(Optional.of(oldKey))
                        .givenCurrentPauseKey(Optional.of(oldKey))
                        .givenCurrentFeeScheduleKey(Optional.of(oldKey));
    }

    private long curSize(Key oldKey) {
        return oldSymbol.length()
                + oldName.length()
                + oldMemo.length()
                + 7 * FeeBuilder.getAccountKeyStorageSize(oldKey);
    }

    private long newRb() {
        return symbol.length()
                + name.length()
                + memo.length()
                + FeeBuilder.getAccountKeyStorageSize(adminKey)
                + FeeBuilder.getAccountKeyStorageSize(kycKey)
                + FeeBuilder.getAccountKeyStorageSize(wipeKey)
                + FeeBuilder.getAccountKeyStorageSize(supplyKey)
                + FeeBuilder.getAccountKeyStorageSize(freezeKey)
                + BASIC_ENTITY_ID_SIZE;
    }

    private void givenOp() {
        givenOp(expiry);
    }

    private void givenOp(long newExpiry) {
        op =
                TokenUpdateTransactionBody.newBuilder()
                        .setToken(id)
                        .setMemo(StringValue.newBuilder().setValue(memo).build())
                        .setExpiry(Timestamp.newBuilder().setSeconds(newExpiry))
                        .setTreasury(treasury)
                        .setAutoRenewAccount(autoRenewAccount)
                        .setSymbol(symbol)
                        .setName(name)
                        .setKycKey(kycKey)
                        .setAdminKey(adminKey)
                        .setFreezeKey(freezeKey)
                        .setSupplyKey(supplyKey)
                        .setWipeKey(wipeKey)
                        .build();
        setTxn();
    }

    private void setTxn() {
        txn =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder()
                                        .setTransactionValidStart(
                                                Timestamp.newBuilder().setSeconds(now)))
                        .setTokenUpdate(op)
                        .build();
    }
}
