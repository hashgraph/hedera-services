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
package com.hedera.node.app.service.token.impl.test;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.token.impl.test.util.AdapterUtils.txnFrom;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.*;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.impl.RebuiltStateImpl;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.TokenPreTransactionHandlerImpl;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PreHandleTokenCreateTest {

    private final Timestamp consensusTimestamp =
            Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private final AccountID payer = asAccount("0.0.3");

    private final AccountID receiverSigRequiredAcc = asAccount("0.0.1338");

    private final HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();

    private final HederaKey adminKeyAsHederaKey = asHederaKey(A_COMPLEX_KEY).get();
    private final Key adminKey = A_COMPLEX_KEY;
    private final Long payerNum = payer.getAccountNum();

    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";

    @Mock private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;
    @Mock private States states;
    @Mock private MerkleAccount payerAccount;
    @Mock private ReadableTokenStore tokenStore;
    @Mock private PreHandleContext context;

    private ReadableAccountStore accountStore;
    private TokenPreTransactionHandlerImpl subject;

    @BeforeEach
    void setUp() {
        given(states.get(ACCOUNTS)).willReturn(accounts);
        given(states.get(ALIASES)).willReturn(aliases);
        given(accounts.get(payerNum)).willReturn(Optional.of(payerAccount));
        given(payerAccount.getAccountKey()).willReturn((JKey) payerKey);

        accountStore = new ReadableAccountStore(states);

        subject = new TokenPreTransactionHandlerImpl(accountStore, tokenStore, context);
    }

    @Test
    void tokenCreateAdminKeyOnly() {
        final var createTxBody =
                TokenCreateTransactionBody.newBuilder()
                        .setTreasury(payer)
                        .setAutoRenewAccount(payer)
                        .setAdminKey(adminKey)
                        .build();

        final var txn = tokenCreateTransaction(createTxBody);
        final var expectedMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(txn)
                        .build();

        final var meta = subject.preHandleCreateToken(txn, payer);
        assertEquals(expectedMeta.txnBody(), meta.txnBody());
        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
        assertEquals(meta.payerKey(), payerKey);
    }

    @Test
    void tokenCreateWithAdminKeyOnlyOld() {
        final var meta = subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_ADMIN_ONLY), payer);

        basicMetaAssertions(meta, 0, true, ResponseCodeEnum.OK);
    }

    @Test
    void getsTokenCreateMissingAdminKey() {
        final var createTxBody =
                TokenCreateTransactionBody.newBuilder()
                        .setTreasury(payer)
                        .setAutoRenewAccount(payer)
                        .build();

        final var txn = tokenCreateTransaction(createTxBody);
        final var expectedMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(txn)
                        .build();

        final var meta = subject.preHandleCreateToken(txn, payer);
        assertEquals(expectedMeta.txnBody(), meta.txnBody());
        basicMetaAssertions(meta, 0, false, ResponseCodeEnum.OK);
    }

    @Test
    void getsTokenCreateMissingTreasuryKey() {
        final var createTxBody =
                TokenCreateTransactionBody.newBuilder().setAutoRenewAccount(payer).build();

        final var txn = tokenCreateTransaction(createTxBody);
        final var expectedMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(txn)
                        .build();

        final var meta = subject.preHandleCreateToken(txn, payer);
        assertEquals(expectedMeta.txnBody(), meta.txnBody());
        basicMetaAssertions(meta, 0, true, ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
    }

    @Test
    void tokenCreateWithMissingAutoRenew() {
        final var createTxBody = TokenCreateTransactionBody.newBuilder().setTreasury(payer).build();

        final var txn = tokenCreateTransaction(createTxBody);
        final var expectedMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(txn)
                        .build();

        final var meta = subject.preHandleCreateToken(txn, payer);
        assertEquals(expectedMeta.txnBody(), meta.txnBody());
        basicMetaAssertions(meta, 0, true, ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
    }

    //    @Test
    //    void tokenCreateTreasuryAsCustomPayer() {
    //
    //        final var createTxBody = TokenCreateTransactionBody.newBuilder()
    //                .setTreasury(customPayer)
    //                .setAutoRenewAccount(payer)
    //                .build();
    //
    //        final var txn = tokenCreateTransaction(createTxBody);
    //        final var expectedMeta =
    //                new SigTransactionMetadataBuilder(accountStore)
    //                        .payerKeyFor(customPayer)
    //                        .txnBody(txn)
    //                        .build();
    //
    //        final var meta = subject.preHandleCreateToken(txn, customPayer);
    //        assertEquals(expectedMeta.txnBody(), meta.txnBody());
    //        basicMetaAssertions(meta, 0, true, ResponseCodeEnum.OK);
    //    }

    //    @Test
    //    void tokenCreateWithAutoRenewAsCustomPayer() throws Throwable {
    //        final var meta =
    //
    // subject.preHandleCreateToken(TOKEN_CREATE_WITH_AUTO_RENEW_AS_CUSTOM_PAYER.platformTxn().getTxn(), payer);
    //
    ////        assertTrue(meta.requiredNonPayerKeys().contains(customPayerKey));
    //        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    //    }

    @Test
    void tokenCreateCustomFeeAndCollectorMissing() {
        final FixedFee fixedFee = FixedFee.newBuilder().setAmount(5).build();
        final CustomFee customFee = CustomFee.newBuilder().setFixedFee(fixedFee).build();
        final var createTxBody =
                TokenCreateTransactionBody.newBuilder()
                        .setAutoRenewAccount(payer)
                        .setTreasury(payer)
                        .addCustomFees(customFee)
                        .build();

        final var txn = tokenCreateTransaction(createTxBody);
        final var expectedMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(txn)
                        .build();

        final var meta = subject.preHandleCreateToken(txn, payer);
        assertEquals(expectedMeta.txnBody(), meta.txnBody());
        basicMetaAssertions(meta, 0, true, ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR);
    }

    @Test
    void tokenCreateCustomFixedFeeNoCollectorSigReq() {
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ), payer);

        basicMetaAssertions(meta, 0, true, ResponseCodeEnum.OK);
    }

    // TODO
    @Test
    void tokenCreateCustomFixedFeeAndCollectorSigReq() {
        final FixedFee fixedFee = FixedFee.newBuilder().build();
        final CustomFee customFee =
                CustomFee.newBuilder()
                        .setFixedFee(fixedFee)
                        .setFeeCollectorAccountId(receiverSigRequiredAcc)
                        .build();
        final var createTxBody =
                TokenCreateTransactionBody.newBuilder()
                        .setAutoRenewAccount(payer)
                        .setTreasury(payer)
                        .addCustomFees(customFee)
                        .build();

        final var txn = tokenCreateTransaction(createTxBody);
        final var expectedMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(txn)
                        .build();

        final var meta = subject.preHandleCreateToken(txn, payer);
        assertEquals(expectedMeta.txnBody(), meta.txnBody());
        basicMetaAssertions(meta, 0, true, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateWithAutoRenewAsPayer() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW_AS_PAYER), payer);

        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }
    //

    //    @Test
    //    void tokenCreateCustomFixedFeeAndCollectorSigReqAndAsPayer() {
    //        final var meta =
    //                subject.preHandleCreateToken(
    //                        txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER),
    // payer);
    //
    //        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    //    }

    //
    //    @Test
    //    void tokenCreateCustomFixedFeeNoCollectorSigReqButDenomWildcard() {
    //        final var meta =
    //                subject.preHandleCreateToken(
    //                        txnFrom(
    //
    // TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ_BUT_USING_WILDCARD_DENOM),
    //                        payer);
    //
    //        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    //    }
    //
    //    @Test
    //    void tokenCreateCustomFractionalFeeNoCollectorSigReq() {
    //        final var meta =
    //                subject.preHandleCreateToken(
    //                        txnFrom(TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ),
    // payer);
    //
    //        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    //    }
    //
    //    @Test
    //    void tokenCreateCustomRoyaltyFeeFallbackNoWildcardButSigReq() {
    //        final var meta =
    //                subject.preHandleCreateToken(
    //                        txnFrom(
    //
    // TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_NO_WILDCARD_BUT_SIG_REQ),
    //                        payer);
    //
    //        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    //    }
    //
    //    @Test
    //    void tokenCreateCustomRoyaltyFeeFallbackWildcardNoSigReq() {
    //        final var meta =
    //                subject.preHandleCreateToken(
    //                        txnFrom(
    //
    // TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_NO_SIG_REQ),
    //                        payer);
    //
    //        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    //    }
    //
    //    @Test
    //    void tokenCreateCustomRoyaltyFeeNoFallbackAndNoCollectorSigReq() {
    //        final var meta =
    //                subject.preHandleCreateToken(
    //
    // txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_NO_SIG_REQ_NO_FALLBACK),
    //                        payer);
    //
    //        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    //    }
    //
    //    @Test
    //    void tokenCreateCustomRoyaltyFeeNoFallbackButSigReq() {
    //        final var meta =
    //                subject.preHandleCreateToken(
    //                        txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_SIG_REQ_NO_FALLBACK),
    //                        payer);
    //
    //        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    //    }

    private TransactionBody tokenCreateTransaction(final TokenCreateTransactionBody createTxBody) {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(consensusTimestamp);

        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setTokenCreation(createTxBody)
                .build();
    }

    private void basicMetaAssertions(
            final TransactionMetadata meta,
            final int keysSize,
            final boolean failed,
            final ResponseCodeEnum failureStatus) {
        assertEquals(keysSize, meta.requiredNonPayerKeys().size());
        assertEquals(failed, meta.failed());
        assertEquals(failureStatus, meta.status());
    }
}
