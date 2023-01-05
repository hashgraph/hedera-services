/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
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
    private final AccountID DEFAULT_PAYER = asAccount("0.0.13257");

    private final AccountID NON_SIG_REQUIRED_ID = asAccount("0.0.1337");
    private final AccountID SIG_REQUIRED_ID = asAccount("0.0.1338");
    private final AccountID TREASURY_ACCOUNT_ID = asAccount("0.0.1341");
    private final HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();
    private final HederaKey randomKey = asHederaKey(A_COMPLEX_KEY).get();
    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";

    @Mock private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;
    @Mock private States states;
    @Mock private MerkleAccount payerAccount;
    @Mock private MerkleAccount treasuryAccount;
    @Mock private MerkleAccount autoRenewAccount;
    @Mock private ReadableTokenStore tokenStore;
    @Mock private PreHandleContext context;

    private ReadableAccountStore accountStore;
    private TokenPreTransactionHandlerImpl subject;

    @BeforeEach
    void setUp() {
        given(states.get(ACCOUNTS)).willReturn(accounts);
        given(states.get(ALIASES)).willReturn(aliases);

        accountStore = new ReadableAccountStore(states);

        subject = new TokenPreTransactionHandlerImpl(accountStore, tokenStore, context);
    }

    @Test
    void tokenCreateWithAdminKey() {
        setPayerAccount();
        setTreasuryAccount();
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_ADMIN_ONLY), DEFAULT_PAYER);

        assertEquals(payerKey, meta.payerKey());
        basicMetaAssertions(meta, 2, false, OK);
    }

    @Test
    void tokenCreateMissingAdminKey() {
        setPayerAccount();
        setTreasuryAccount();

        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_MISSING_ADMIN), DEFAULT_PAYER);

        assertEquals(payerKey, meta.payerKey());
        basicMetaAssertions(meta, 1, false, OK);
    }

    @Test
    void tokenCreateMissingTreasuryKey() {
        setPayerAccount();
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_MISSING_TREASURY), DEFAULT_PAYER);

        assertEquals(payerKey, meta.payerKey());
        basicMetaAssertions(meta, 0, true, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
    }

    @Test
    void tokenCreateTreasuryAsPayer() {
        setTreasuryAccount();
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_TREASURY_AS_PAYER), TREASURY_ACCOUNT_ID);

        basicMetaAssertions(meta, 0, true, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
    }

    @Test
    void tokenCreateMissingAutoRenew() {
        setPayerAccount();
        setTreasuryAccount();

        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_MISSING_AUTO_RENEW), DEFAULT_PAYER);

        assertEquals(payerKey, meta.payerKey());
        basicMetaAssertions(meta, 1, true, INVALID_AUTORENEW_ACCOUNT);
    }

    @Test
    void tokenCreateCustomFeeAndCollectorMissing() {
        setPayerAccount();
        setTreasuryAccount();

        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_MISSING_COLLECTOR), DEFAULT_PAYER);

        assertEquals(payerKey, meta.payerKey());
        basicMetaAssertions(meta, 1, true, INVALID_CUSTOM_FEE_COLLECTOR);
    }

    @Test
    void tokenCreateWithAutoRenewAsPayer() {
        setTreasuryAccount();
        given(accounts.get(DEFAULT_PAYER.getAccountNum()))
                .willReturn(Optional.of(autoRenewAccount));
        given(autoRenewAccount.getAccountKey()).willReturn((JKey) randomKey);
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW_AS_PAYER), DEFAULT_PAYER);

        assertEquals(meta.payerKey(), autoRenewAccount.getAccountKey());
        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeNoCollectorSigReq() {
        setTreasuryAccount();
        setNoSigRequiredAccount();
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ),
                        NON_SIG_REQUIRED_ID);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeAndCollectorSigReq() {
        setTreasuryAccount();
        setSigRequiredAccount();
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ), SIG_REQUIRED_ID);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeNoCollectorSigReqButDenomWildcard() {
        setTreasuryAccount();
        setNoSigRequiredAccount();
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(
                                TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ_BUT_USING_WILDCARD_DENOM),
                        NON_SIG_REQUIRED_ID);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFractionalFeeNoCollectorSigReq() {
        setTreasuryAccount();
        setNoSigRequiredAccount();
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ),
                        NON_SIG_REQUIRED_ID);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeFallbackNoWildcardButSigReq() {
        setTreasuryAccount();
        setSigRequiredAccount();

        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(
                                TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_NO_WILDCARD_BUT_SIG_REQ),
                        SIG_REQUIRED_ID);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeFallbackWildcardNoSigReq() {
        setTreasuryAccount();
        setNoSigRequiredAccount();

        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(
                                TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_NO_SIG_REQ),
                        NON_SIG_REQUIRED_ID);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeNoFallbackAndNoCollectorSigReq() {
        setTreasuryAccount();
        setNoSigRequiredAccount();
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_NO_SIG_REQ_NO_FALLBACK),
                        NON_SIG_REQUIRED_ID);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeNoFallbackButSigReq() {
        setTreasuryAccount();
        setSigRequiredAccount();
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_SIG_REQ_NO_FALLBACK),
                        SIG_REQUIRED_ID);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeAndCollectorSigReqAndAsPayer() {
        setTreasuryAccount();
        setPayerAccount();
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER),
                        DEFAULT_PAYER);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
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

    private void setPayerAccount() {
        given(accounts.get(DEFAULT_PAYER.getAccountNum())).willReturn(Optional.of(payerAccount));
        given(payerAccount.getAccountKey()).willReturn((JKey) payerKey);
    }

    private void setTreasuryAccount() {
        given(accounts.get(TREASURY_ACCOUNT_ID.getAccountNum()))
                .willReturn(Optional.of(treasuryAccount));
        given(treasuryAccount.getAccountKey()).willReturn((JKey) randomKey);
    }

    private void setSigRequiredAccount() {
        given(accounts.get(SIG_REQUIRED_ID.getAccountNum())).willReturn(Optional.of(payerAccount));
        given(payerAccount.getAccountKey()).willReturn((JKey) randomKey);
    }

    private void setNoSigRequiredAccount() {
        given(accounts.get(NON_SIG_REQUIRED_ID.getAccountNum()))
                .willReturn(Optional.of(payerAccount));
        given(payerAccount.getAccountKey()).willReturn((JKey) randomKey);
    }
}
