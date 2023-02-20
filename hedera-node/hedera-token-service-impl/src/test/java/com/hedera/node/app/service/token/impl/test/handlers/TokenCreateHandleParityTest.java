/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.node.app.service.token.impl.test.util.MetaAssertion.basicContextAssertions;
import static com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils.txnFrom;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_MISSING_ADMIN;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ADMIN_AND_FREEZE;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ADMIN_ONLY;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_AUTO_RENEW;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_AUTO_RENEW_AS_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_AUTO_RENEW_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_USING_WILDCARD_DENOM;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_DENOMINATION_AND_NO_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_INVALID_COLLECTOR;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ_BUT_USING_WILDCARD_DENOM;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_NO_DENOM_AND_NO_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_NO_DENOM_AND_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_MISSING_AUTO_RENEW;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_MISSING_COLLECTOR;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_MISSING_TREASURY;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_NO_WILDCARD_BUT_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_NO_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_NO_SIG_REQ_NO_FALLBACK;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_SIG_REQ_NO_FALLBACK;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_NO_FALLBACK_AND_NO_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_TREASURY_AS_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_TREASURY_AS_PAYER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_TREASURY_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.service.token.impl.handlers.TokenCreateHandler;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.meta.PreHandleContext;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenCreateHandleParityTest {
    private AccountKeyLookup accountStore;
    private TokenCreateHandler subject;

    @BeforeEach
    void setUp() {
        accountStore = AdapterUtils.wellKnownKeyLookupAt();
        subject = new TokenCreateHandler();
    }

    @Test
    void tokenCreateWithAdminKey() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ADMIN_ONLY);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), TOKEN_ADMIN_KT.asKey()));
        basicContextAssertions(context, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateWithAdminKeyAndFreeze() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ADMIN_AND_FREEZE);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), TOKEN_ADMIN_KT.asKey()));
        basicContextAssertions(context, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateMissingAdminKey() {
        final var txn = txnFrom(TOKEN_CREATE_MISSING_ADMIN);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(TOKEN_TREASURY_KT.asKey()));
        basicContextAssertions(context, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateMissingTreasuryKey() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_MISSING_TREASURY);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        basicContextAssertions(context, 0, true, ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
    }

    @Test
    void tokenCreateTreasuryAsPayer() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_TREASURY_AS_PAYER);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        basicContextAssertions(context, 0, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateMissingAutoRenew() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_MISSING_AUTO_RENEW);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        basicContextAssertions(context, 1, true, ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
    }

    @Test
    void tokenCreateWithAutoRenew() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), MISC_ACCOUNT_KT.asKey()));
        basicContextAssertions(context, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateWithAutoRenewAsPayer() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW_AS_PAYER);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(TOKEN_TREASURY_KT.asKey()));
        basicContextAssertions(context, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateWithAutoRenewAsCustomPayer() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW_AS_CUSTOM_PAYER);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), CUSTOM_PAYER_ACCOUNT_KT.asKey()));
        basicContextAssertions(context, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFeeAndCollectorMissing() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_MISSING_COLLECTOR);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(TOKEN_TREASURY_KT.asKey()));
        basicContextAssertions(context, 1, true, ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR);
    }

    @Test
    void tokenCreateCustomFixedFeeNoCollectorSigReq() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(TOKEN_TREASURY_KT.asKey()));
        basicContextAssertions(context, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeInvalidCollector() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_INVALID_COLLECTOR);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        basicContextAssertions(context, 1, true, ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR);
        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(TOKEN_TREASURY_KT.asKey()));
    }

    @Test
    void tokenCreateCustomFixedFeeAndCollectorSigReq() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), RECEIVER_SIG_KT.asKey()));
        basicContextAssertions(context, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeNoCollectorSigReqButDenomWildcard() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ_BUT_USING_WILDCARD_DENOM);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), NO_RECEIVER_SIG_KT.asKey()));
        basicContextAssertions(context, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeCollectorSigReqAndDenomWildcard() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_USING_WILDCARD_DENOM);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), RECEIVER_SIG_KT.asKey()));
        basicContextAssertions(context, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFractionalFeeNoCollectorSigReq() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), NO_RECEIVER_SIG_KT.asKey()));
        basicContextAssertions(context, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeFallbackNoWildcardButSigReq() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_NO_WILDCARD_BUT_SIG_REQ);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), RECEIVER_SIG_KT.asKey()));
        basicContextAssertions(context, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeFallbackWildcardNoSigReq() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_NO_SIG_REQ);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), NO_RECEIVER_SIG_KT.asKey()));
        basicContextAssertions(context, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeFallbackWildcardAndSigReq() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_SIG_REQ);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), RECEIVER_SIG_KT.asKey()));
        basicContextAssertions(context, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeNoFallbackAndNoCollectorSigReq() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_NO_SIG_REQ_NO_FALLBACK);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(TOKEN_TREASURY_KT.asKey()));
        basicContextAssertions(context, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeNoFallbackButSigReq() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_SIG_REQ_NO_FALLBACK);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), RECEIVER_SIG_KT.asKey()));
        basicContextAssertions(context, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateTreasuryAsCustomPayer() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_TREASURY_AS_CUSTOM_PAYER);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
        basicContextAssertions(context, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeAndCollectorSigReqAndAsPayer() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(TOKEN_TREASURY_KT.asKey()));
        basicContextAssertions(context, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeNoSigRequiredWithPositiveDenom() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_DENOMINATION_AND_NO_SIG_REQ);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(TOKEN_TREASURY_KT.asKey()));
        basicContextAssertions(context, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedNoDenomWithSigRequired() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_NO_DENOM_AND_SIG_REQ);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), RECEIVER_SIG_KT.asKey()));
        basicContextAssertions(context, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedNoDenomNoSigRequired() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_NO_DENOM_AND_NO_SIG_REQ);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(TOKEN_TREASURY_KT.asKey()));
        basicContextAssertions(context, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeNoSigRequiredWithPositiveDenom() {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_NO_FALLBACK_AND_NO_SIG_REQ);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(TOKEN_TREASURY_KT.asKey()));
        basicContextAssertions(context, 1, false, ResponseCodeEnum.OK);
    }
}
