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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.TOKEN_REPLACE_KT;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.TOKEN_UPDATE_WITH_MISSING_AUTO_RENEW_ACCOUNT;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_REPLACING_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_REPLACING_TREASURY;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_REPLACING_TREASURY_AS_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_REPLACING_TREASURY_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_REPLACING_WITH_MISSING_TREASURY;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_FREEZE_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_KYC_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_MISSING_TOKEN_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_NO_KEYS_AFFECTED;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_SUPPLY_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_WIPE_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_TREASURY_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.service.token.impl.handlers.TokenUpdateHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenUpdateHandlerParityTest extends ParityTestBase {
    private final TokenUpdateHandler subject = new TokenUpdateHandler();

    @BeforeEach
    void setUp() {
        super.setUp();
    }

    @Test
    void tokenUpdateWithoutAffectingKeys() throws PreCheckException {
        final var txn = txnFrom(UPDATE_WITH_NO_KEYS_AFFECTED);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(context.requiredNonPayerKeys(), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateReplacingTreasury() throws PreCheckException {
        final var txn = txnFrom(UPDATE_REPLACING_TREASURY);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(2, context.requiredNonPayerKeys().size());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_ADMIN_KT.asPbjKey(), TOKEN_TREASURY_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateReplacingTreasuryWithPayer() throws PreCheckException {
        final var txn = txnFrom(UPDATE_REPLACING_TREASURY_AS_PAYER);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(context.requiredNonPayerKeys(), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateReplacingTreasuryWithCustomPayer() throws PreCheckException {
        final var txn = txnFrom(UPDATE_REPLACING_TREASURY_AS_CUSTOM_PAYER);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(2, context.requiredNonPayerKeys().size());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_ADMIN_KT.asPbjKey(), CUSTOM_PAYER_ACCOUNT_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateReplacingTreasuryWithNonExistingAccount() throws PreCheckException {
        final var txn = txnFrom(UPDATE_REPLACING_WITH_MISSING_TREASURY);
        final var context = new PreHandleContext(readableAccountStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context, readableTokenStore), INVALID_ACCOUNT_ID);
    }

    @Test
    void tokenUpdateReplacingAdminKey() throws PreCheckException {
        final var txn = txnFrom(UPDATE_REPLACING_ADMIN_KEY);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(2, context.requiredNonPayerKeys().size());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_ADMIN_KT.asPbjKey(), TOKEN_REPLACE_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateWithSupplyKeyedToken() throws PreCheckException {
        final var txn = txnFrom(UPDATE_WITH_SUPPLY_KEYED_TOKEN);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(context.requiredNonPayerKeys(), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateWithKYCKeyedToken() throws PreCheckException {
        final var txn = txnFrom(UPDATE_WITH_KYC_KEYED_TOKEN);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(context.requiredNonPayerKeys(), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateWithFreezeKeyedToken() throws PreCheckException {
        final var txn = txnFrom(UPDATE_WITH_FREEZE_KEYED_TOKEN);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(context.requiredNonPayerKeys(), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateWithWipeKeyedToken() throws PreCheckException {
        final var txn = txnFrom(UPDATE_WITH_WIPE_KEYED_TOKEN);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(context.requiredNonPayerKeys(), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateMissingToken() throws PreCheckException {
        final var txn = txnFrom(UPDATE_WITH_MISSING_TOKEN);
        final var context = new PreHandleContext(readableAccountStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context, readableTokenStore), INVALID_TOKEN_ID);
    }

    @Test
    void tokenUpdateTokenWithoutAdminKey() throws PreCheckException {
        final var txn = txnFrom(UPDATE_WITH_MISSING_TOKEN_ADMIN_KEY);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(0, context.requiredNonPayerKeys().size());
    }

    @Test
    void tokenUpdateTokenWithNewAutoRenewAccount() throws PreCheckException {
        final var txn = txnFrom(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(2, context.requiredNonPayerKeys().size());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_ADMIN_KT.asPbjKey(), MISC_ACCOUNT_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateTokenWithNewAutoRenewAccountAsPayer() throws PreCheckException {
        final var txn = txnFrom(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_PAYER);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(context.requiredNonPayerKeys(), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateTokenWithNewAutoRenewAccountAsCustomPayer() throws PreCheckException {
        final var txn = txnFrom(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_CUSTOM_PAYER);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(2, context.requiredNonPayerKeys().size());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(TOKEN_ADMIN_KT.asPbjKey(), CUSTOM_PAYER_ACCOUNT_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateTokenWithMissingNewAutoRenewAccount() throws PreCheckException {
        final var txn = txnFrom(TOKEN_UPDATE_WITH_MISSING_AUTO_RENEW_ACCOUNT);
        final var context = new PreHandleContext(readableAccountStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context, readableTokenStore), INVALID_AUTORENEW_ACCOUNT);
    }
}
