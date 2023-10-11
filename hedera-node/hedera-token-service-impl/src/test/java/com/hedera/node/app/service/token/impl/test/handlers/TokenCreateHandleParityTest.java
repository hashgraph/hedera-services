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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hedera.node.app.service.token.impl.test.util.MetaAssertion.basicContextAssertions;
import static com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils.txnFrom;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.TokenCreateHandler;
import com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils;
import com.hedera.node.app.service.token.impl.validators.CustomFeesValidator;
import com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator;
import com.hedera.node.app.service.token.impl.validators.TokenCreateValidator;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenCreateHandleParityTest {
    private ReadableAccountStore accountStore;
    private TokenCreateHandler subject;
    private CustomFeesValidator customFeesValidator;
    private TokenAttributesValidator tokenFieldsValidator;
    private TokenCreateValidator tokenCreateValidator;

    @BeforeEach
    void setUp() {
        tokenFieldsValidator = new TokenAttributesValidator();
        customFeesValidator = new CustomFeesValidator();
        tokenCreateValidator = new TokenCreateValidator(tokenFieldsValidator);
        accountStore = SigReqAdapterUtils.wellKnownAccountStoreAt();
        subject = new TokenCreateHandler(customFeesValidator, tokenCreateValidator);
    }

    @Test
    void tokenCreateWithAdminKey() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ADMIN_ONLY);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_TREASURY_KT.asPbjKey(), TOKEN_ADMIN_KT.asPbjKey()));
        basicContextAssertions(context, 2);
    }

    @Test
    void tokenCreateWithAdminKeyAndFreeze() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ADMIN_AND_FREEZE);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_TREASURY_KT.asPbjKey(), TOKEN_ADMIN_KT.asPbjKey()));
        basicContextAssertions(context, 2);
    }

    @Test
    void tokenCreateMissingAdminKey() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_MISSING_ADMIN);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(TOKEN_TREASURY_KT.asPbjKey()));
        basicContextAssertions(context, 1);
    }

    @Test
    void tokenCreateMissingTreasuryKey() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_MISSING_TREASURY);

        final var context = new FakePreHandleContext(accountStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
    }

    @Test
    void tokenCreateTreasuryAsPayer() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_TREASURY_AS_PAYER);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        basicContextAssertions(context, 0);
    }

    @Test
    void tokenCreateMissingAutoRenew() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_MISSING_AUTO_RENEW);

        final var context = new FakePreHandleContext(accountStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_AUTORENEW_ACCOUNT);
    }

    @Test
    void tokenCreateWithAutoRenew() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_TREASURY_KT.asPbjKey(), MISC_ACCOUNT_KT.asPbjKey()));
        basicContextAssertions(context, 2);
    }

    @Test
    void tokenCreateWithAutoRenewAsPayer() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW_AS_PAYER);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(TOKEN_TREASURY_KT.asPbjKey()));
        basicContextAssertions(context, 1);
    }

    @Test
    void tokenCreateWithAutoRenewAsCustomPayer() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW_AS_CUSTOM_PAYER);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_TREASURY_KT.asPbjKey(), CUSTOM_PAYER_ACCOUNT_KT.asPbjKey()));
        basicContextAssertions(context, 2);
    }

    @Test
    void tokenCreateCustomFeeAndCollectorMissing() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_MISSING_COLLECTOR);

        final var context = new FakePreHandleContext(accountStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_CUSTOM_FEE_COLLECTOR);
    }

    @Test
    void tokenCreateCustomFixedFeeNoCollectorSigReq() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertTrue(context.requiredNonPayerKeys().contains(TOKEN_TREASURY_KT.asPbjKey()));
        basicContextAssertions(context, 1);
    }

    @Test
    void tokenCreateCustomFixedFeeInvalidCollector() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_INVALID_COLLECTOR);

        final var context = new FakePreHandleContext(accountStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_CUSTOM_FEE_COLLECTOR);
    }

    @Test
    void tokenCreateCustomFixedFeeAndCollectorSigReq() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_TREASURY_KT.asPbjKey(), RECEIVER_SIG_KT.asPbjKey()));
        basicContextAssertions(context, 2);
    }

    @Test
    void tokenCreateCustomFixedFeeNoCollectorSigReqButDenomWildcard() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ_BUT_USING_WILDCARD_DENOM);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_TREASURY_KT.asPbjKey(), NO_RECEIVER_SIG_KT.asPbjKey()));
        basicContextAssertions(context, 2);
    }

    @Test
    void tokenCreateCustomFixedFeeCollectorSigReqAndDenomWildcard() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_USING_WILDCARD_DENOM);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_TREASURY_KT.asPbjKey(), RECEIVER_SIG_KT.asPbjKey()));
        basicContextAssertions(context, 2);
    }

    @Test
    void tokenCreateCustomFractionalFeeNoCollectorSigReq() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_TREASURY_KT.asPbjKey(), NO_RECEIVER_SIG_KT.asPbjKey()));
        basicContextAssertions(context, 2);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeFallbackNoWildcardButSigReq() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_NO_WILDCARD_BUT_SIG_REQ);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_TREASURY_KT.asPbjKey(), RECEIVER_SIG_KT.asPbjKey()));
        basicContextAssertions(context, 2);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeFallbackWildcardNoSigReq() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_NO_SIG_REQ);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_TREASURY_KT.asPbjKey(), NO_RECEIVER_SIG_KT.asPbjKey()));
        basicContextAssertions(context, 2);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeFallbackWildcardAndSigReq() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_SIG_REQ);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_TREASURY_KT.asPbjKey(), RECEIVER_SIG_KT.asPbjKey()));
        basicContextAssertions(context, 2);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeNoFallbackAndNoCollectorSigReq() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_NO_SIG_REQ_NO_FALLBACK);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertTrue(context.requiredNonPayerKeys().contains(TOKEN_TREASURY_KT.asPbjKey()));
        basicContextAssertions(context, 1);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeNoFallbackButSigReq() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_SIG_REQ_NO_FALLBACK);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_TREASURY_KT.asPbjKey(), RECEIVER_SIG_KT.asPbjKey()));
        basicContextAssertions(context, 2);
    }

    @Test
    void tokenCreateTreasuryAsCustomPayer() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_TREASURY_AS_CUSTOM_PAYER);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(CUSTOM_PAYER_ACCOUNT_KT.asPbjKey()));
        basicContextAssertions(context, 1);
    }

    @Test
    void tokenCreateCustomFixedFeeAndCollectorSigReqAndAsPayer() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(TOKEN_TREASURY_KT.asPbjKey()));
        basicContextAssertions(context, 1);
    }

    @Test
    void tokenCreateCustomFixedFeeNoSigRequiredWithPositiveDenom() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_DENOMINATION_AND_NO_SIG_REQ);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(TOKEN_TREASURY_KT.asPbjKey()));
        basicContextAssertions(context, 1);
    }

    @Test
    void tokenCreateCustomFixedNoDenomWithSigRequired() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_NO_DENOM_AND_SIG_REQ);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_TREASURY_KT.asPbjKey(), RECEIVER_SIG_KT.asPbjKey()));
        basicContextAssertions(context, 2);
    }

    @Test
    void tokenCreateCustomFixedNoDenomNoSigRequired() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_NO_DENOM_AND_NO_SIG_REQ);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertTrue(context.requiredNonPayerKeys().contains(TOKEN_TREASURY_KT.asPbjKey()));
        basicContextAssertions(context, 1);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeNoSigRequiredWithPositiveDenom() throws PreCheckException {
        final var txn = txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_NO_FALLBACK_AND_NO_SIG_REQ);

        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(TOKEN_TREASURY_KT.asPbjKey()));
        basicContextAssertions(context, 1);
    }
}
