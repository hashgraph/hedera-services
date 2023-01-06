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
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_MISSING_ADMIN;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ADMIN_ONLY;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_AUTO_RENEW;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_AUTO_RENEW_AS_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_AUTO_RENEW_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ_BUT_USING_WILDCARD_DENOM;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_MISSING_AUTO_RENEW;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_MISSING_COLLECTOR;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_MISSING_TREASURY;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_NO_WILDCARD_BUT_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_NO_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_NO_SIG_REQ_NO_FALLBACK;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_SIG_REQ_NO_FALLBACK;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_TREASURY_AS_PAYER;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.impl.RebuiltStateImpl;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.TokenCreateHandler;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreHandleTokenCreateTest {
    private final AccountID DEFAULT_PAYER_ID = asAccount("0.0.13257");
    private final AccountID NON_SIG_REQUIRED_ID = asAccount("0.0.1337");
    private final AccountID SIG_REQUIRED_ID = asAccount("0.0.1338");
    private final AccountID AUTO_RENEW_ID = asAccount("0.0.1339");
    private final AccountID TREASURY_ACCOUNT_ID = asAccount("0.0.1341");
    private final AccountID CUSTOM_PAYER_ACCOUNT_ID = asAccount("0.0.1216");
    private final HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();
    private final HederaKey randomKey = asHederaKey(A_COMPLEX_KEY).get();
    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";

    @Mock private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;
    @Mock private States states;
    @Mock private MerkleAccount payerAccount;
    @Mock private MerkleAccount customPayerAccount;
    @Mock private MerkleAccount sigRequiredAccount;
    @Mock private MerkleAccount noSigRequiredAccount;
    @Mock private MerkleAccount treasuryAccount;
    @Mock private MerkleAccount autoRenewAccount;

    private ReadableAccountStore accountStore;
    private TokenCreateHandler subject;

    @BeforeEach
    void setUp() {
        given(states.get(ACCOUNTS)).willReturn(accounts);
        given(states.get(ALIASES)).willReturn(aliases);

        accountStore = new ReadableAccountStore(states);
        subject = new TokenCreateHandler();
    }

    @Test
    void tokenCreateWithAdminKey() {
        setPayerAccount();
        setTreasuryAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_ADMIN_ONLY), DEFAULT_PAYER_ID, accountStore);

        assertEquals(DEFAULT_PAYER_ID, meta.payer());
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateMissingAdminKey() {
        setPayerAccount();
        setTreasuryAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_MISSING_ADMIN), DEFAULT_PAYER_ID, accountStore);

        assertEquals(DEFAULT_PAYER_ID, meta.payer());
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateMissingTreasuryKey() {
        setPayerAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_MISSING_TREASURY),
                        DEFAULT_PAYER_ID,
                        accountStore);

        assertEquals(DEFAULT_PAYER_ID, meta.payer());
        basicMetaAssertions(meta, 0, true, ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
    }

    @Test
    void tokenCreateTreasuryAsPayer() {
        setTreasuryAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_TREASURY_AS_PAYER),
                        TREASURY_ACCOUNT_ID,
                        accountStore);

        assertEquals(TREASURY_ACCOUNT_ID, meta.payer());
        basicMetaAssertions(meta, 0, true, ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
    }

    @Test
    void tokenCreateMissingAutoRenew() {
        setPayerAccount();
        setTreasuryAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_MISSING_AUTO_RENEW),
                        DEFAULT_PAYER_ID,
                        accountStore);

        assertEquals(DEFAULT_PAYER_ID, meta.payer());
        basicMetaAssertions(meta, 1, true, ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
    }

    @Test
    void tokenCreateWithAutoRenew() {
        setPayerAccount();
        setTreasuryAccount();
        setAutoRenewAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW), DEFAULT_PAYER_ID, accountStore);

        assertEquals(meta.payer(), DEFAULT_PAYER_ID);
        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateWithAutoRenewAsPayer() {
        setPayerAccount();
        setTreasuryAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW_AS_PAYER),
                        DEFAULT_PAYER_ID,
                        accountStore);

        assertEquals(meta.payer(), DEFAULT_PAYER_ID);
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateWithAutoRenewAsPayerWithCustomPayer() {
        setPayerAccount();
        given(accounts.get(CUSTOM_PAYER_ACCOUNT_ID.getAccountNum()))
                .willReturn(Optional.of(autoRenewAccount));
        given(autoRenewAccount.getAccountKey()).willReturn((JKey) randomKey);
        setTreasuryAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW_AS_PAYER),
                        CUSTOM_PAYER_ACCOUNT_ID,
                        accountStore);

        assertEquals(meta.payer(), CUSTOM_PAYER_ACCOUNT_ID);
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        assertTrue(meta.requiredNonPayerKeys().contains(autoRenewAccount.getAccountKey()));
        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateWithAutoRenewAsCustomPayer() {
        setPayerAccount();
        setCustomPayerAccount();
        setTreasuryAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW_AS_CUSTOM_PAYER),
                        DEFAULT_PAYER_ID,
                        accountStore);

        assertEquals(meta.payer(), DEFAULT_PAYER_ID);
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        assertTrue(meta.requiredNonPayerKeys().contains(customPayerAccount.getAccountKey()));
        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFeeAndCollectorMissing() {
        setPayerAccount();
        setTreasuryAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_MISSING_COLLECTOR),
                        DEFAULT_PAYER_ID,
                        accountStore);

        assertEquals(DEFAULT_PAYER_ID, meta.payer());
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        basicMetaAssertions(meta, 1, true, ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR);
    }

    @Test
    void tokenCreateCustomFixedFeeNoCollectorSigReq() {
        setTreasuryAccount();
        setNoSigRequiredAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ),
                        NON_SIG_REQUIRED_ID,
                        accountStore);

        assertEquals(meta.payer(), NON_SIG_REQUIRED_ID);
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeAndCollectorSigReq() {
        setPayerAccount();
        setSigRequiredAccount();
        setTreasuryAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ),
                        DEFAULT_PAYER_ID,
                        accountStore);

        assertEquals(meta.payer(), DEFAULT_PAYER_ID);
        assertTrue(meta.requiredNonPayerKeys().contains(sigRequiredAccount.getAccountKey()));
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeNoCollectorSigReqButDenomWildcard() {
        setPayerAccount();
        setTreasuryAccount();
        setNoSigRequiredAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(
                                TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ_BUT_USING_WILDCARD_DENOM),
                        DEFAULT_PAYER_ID,
                        accountStore);

        assertEquals(meta.payer(), DEFAULT_PAYER_ID);
        assertTrue(meta.requiredNonPayerKeys().contains(noSigRequiredAccount.getAccountKey()));
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFractionalFeeNoCollectorSigReq() {
        setPayerAccount();
        setTreasuryAccount();
        setNoSigRequiredAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ),
                        DEFAULT_PAYER_ID,
                        accountStore);

        assertEquals(meta.payer(), DEFAULT_PAYER_ID);
        assertTrue(meta.requiredNonPayerKeys().contains(noSigRequiredAccount.getAccountKey()));
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeFallbackNoWildcardButSigReq() {
        setPayerAccount();
        setTreasuryAccount();
        setSigRequiredAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(
                                TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_NO_WILDCARD_BUT_SIG_REQ),
                        DEFAULT_PAYER_ID,
                        accountStore);

        assertEquals(meta.payer(), DEFAULT_PAYER_ID);
        assertTrue(meta.requiredNonPayerKeys().contains(sigRequiredAccount.getAccountKey()));
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeFallbackWildcardNoSigReq() {
        setPayerAccount();
        setTreasuryAccount();
        setNoSigRequiredAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(
                                TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_NO_SIG_REQ),
                        DEFAULT_PAYER_ID,
                        accountStore);

        assertEquals(DEFAULT_PAYER_ID, meta.payer());
        assertTrue(meta.requiredNonPayerKeys().contains(noSigRequiredAccount.getAccountKey()));
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeNoFallbackAndNoCollectorSigReq() {
        setTreasuryAccount();
        setNoSigRequiredAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_NO_SIG_REQ_NO_FALLBACK),
                        NON_SIG_REQUIRED_ID,
                        accountStore);

        assertEquals(NON_SIG_REQUIRED_ID, meta.payer());
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeNoFallbackButSigReq() {
        setPayerAccount();
        setTreasuryAccount();
        setSigRequiredAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_SIG_REQ_NO_FALLBACK),
                        DEFAULT_PAYER_ID,
                        accountStore);

        assertEquals(DEFAULT_PAYER_ID, meta.payer());
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        assertTrue(meta.requiredNonPayerKeys().contains(sigRequiredAccount.getAccountKey()));
        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeAndCollectorSigReqAndAsPayer() {
        setPayerAccount();
        setTreasuryAccount();

        final var meta =
                subject.preHandle(
                        txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER),
                        DEFAULT_PAYER_ID,
                        accountStore);

        assertEquals(DEFAULT_PAYER_ID, meta.payer());
        assertTrue(meta.requiredNonPayerKeys().contains(treasuryAccount.getAccountKey()));
        assertFalse(meta.requiredNonPayerKeys().contains(payerAccount.getAccountKey()));
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
        given(accounts.get(DEFAULT_PAYER_ID.getAccountNum())).willReturn(Optional.of(payerAccount));
        given(payerAccount.getAccountKey()).willReturn((JKey) payerKey);
    }

    private void setCustomPayerAccount() {
        given(accounts.get(CUSTOM_PAYER_ACCOUNT_ID.getAccountNum()))
                .willReturn(Optional.of(customPayerAccount));
        given(customPayerAccount.getAccountKey()).willReturn((JKey) payerKey);
    }

    private void setTreasuryAccount() {
        given(accounts.get(TREASURY_ACCOUNT_ID.getAccountNum()))
                .willReturn(Optional.of(treasuryAccount));
        given(treasuryAccount.getAccountKey()).willReturn((JKey) randomKey);
    }

    private void setAutoRenewAccount() {
        given(accounts.get(AUTO_RENEW_ID.getAccountNum()))
                .willReturn(Optional.of(autoRenewAccount));
        given(autoRenewAccount.getAccountKey()).willReturn((JKey) randomKey);
    }

    private void setSigRequiredAccount() {
        given(accounts.get(SIG_REQUIRED_ID.getAccountNum()))
                .willReturn(Optional.of(sigRequiredAccount));
        given(sigRequiredAccount.getAccountKey()).willReturn((JKey) randomKey);
    }

    private void setNoSigRequiredAccount() {
        given(accounts.get(NON_SIG_REQUIRED_ID.getAccountNum()))
                .willReturn(Optional.of(noSigRequiredAccount));
        given(noSigRequiredAccount.getAccountKey()).willReturn((JKey) randomKey);
    }
}
