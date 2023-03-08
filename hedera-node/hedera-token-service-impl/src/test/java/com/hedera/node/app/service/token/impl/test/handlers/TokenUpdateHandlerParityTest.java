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
import static com.hedera.test.utils.KeyUtils.sanityRestoredToPbj;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.token.impl.handlers.TokenUpdateHandler;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import org.junit.jupiter.api.Test;

class TokenUpdateHandlerParityTest extends ParityTestBase {
    private final TokenUpdateHandler subject = new TokenUpdateHandler();

    @Test
    void tokenUpdateWithoutAffectingKeys() {
        final var txn = txnFrom(UPDATE_WITH_NO_KEYS_AFFECTED);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.getRequiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.getRequiredNonPayerKeys()), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateReplacingTreasury() {
        final var txn = txnFrom(UPDATE_REPLACING_TREASURY);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(2, context.getRequiredNonPayerKeys().size());
        assertThat(
                sanityRestoredToPbj(context.getRequiredNonPayerKeys()),
                contains(TOKEN_ADMIN_KT.asPbjKey(), TOKEN_TREASURY_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateReplacingTreasuryWithPayer() {
        final var txn = txnFrom(UPDATE_REPLACING_TREASURY_AS_PAYER);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.getRequiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.getRequiredNonPayerKeys()), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateReplacingTreasuryWithCustomPayer() {
        final var txn = txnFrom(UPDATE_REPLACING_TREASURY_AS_CUSTOM_PAYER);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(2, context.getRequiredNonPayerKeys().size());
        assertThat(
                sanityRestoredToPbj(context.getRequiredNonPayerKeys()),
                contains(TOKEN_ADMIN_KT.asPbjKey(), CUSTOM_PAYER_ACCOUNT_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateReplacingTreasuryWithNonExistingAccount() {
        final var txn = txnFrom(UPDATE_REPLACING_WITH_MISSING_TREASURY);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertTrue(context.failed());
        assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.getRequiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.getRequiredNonPayerKeys()), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateReplacingAdminKey() {
        final var txn = txnFrom(UPDATE_REPLACING_ADMIN_KEY);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(2, context.getRequiredNonPayerKeys().size());
        assertThat(
                sanityRestoredToPbj(context.getRequiredNonPayerKeys()),
                contains(TOKEN_ADMIN_KT.asPbjKey(), TOKEN_REPLACE_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateWithSupplyKeyedToken() {
        final var txn = txnFrom(UPDATE_WITH_SUPPLY_KEYED_TOKEN);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.getRequiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.getRequiredNonPayerKeys()), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateWithKYCKeyedToken() {
        final var txn = txnFrom(UPDATE_WITH_KYC_KEYED_TOKEN);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.getRequiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.getRequiredNonPayerKeys()), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateWithFreezeKeyedToken() {
        final var txn = txnFrom(UPDATE_WITH_FREEZE_KEYED_TOKEN);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.getRequiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.getRequiredNonPayerKeys()), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateWithWipeKeyedToken() {
        final var txn = txnFrom(UPDATE_WITH_WIPE_KEYED_TOKEN);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.getRequiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.getRequiredNonPayerKeys()), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateMissingToken() {
        final var txn = txnFrom(UPDATE_WITH_MISSING_TOKEN);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertTrue(context.failed());
        assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(0, context.getRequiredNonPayerKeys().size());
    }

    @Test
    void tokenUpdateTokenWithoutAdminKey() {
        final var txn = txnFrom(UPDATE_WITH_MISSING_TOKEN_ADMIN_KEY);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(0, context.getRequiredNonPayerKeys().size());
    }

    @Test
    void tokenUpdateTokenWithNewAutoRenewAccount() {
        final var txn = txnFrom(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(2, context.getRequiredNonPayerKeys().size());
        assertThat(
                sanityRestoredToPbj(context.getRequiredNonPayerKeys()),
                contains(TOKEN_ADMIN_KT.asPbjKey(), MISC_ACCOUNT_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateTokenWithNewAutoRenewAccountAsPayer() {
        final var txn = txnFrom(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_PAYER);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.getRequiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.getRequiredNonPayerKeys()), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateTokenWithNewAutoRenewAccountAsCustomPayer() {
        final var txn = txnFrom(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_CUSTOM_PAYER);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(2, context.getRequiredNonPayerKeys().size());
        assertThat(
                sanityRestoredToPbj(context.getRequiredNonPayerKeys()),
                contains(TOKEN_ADMIN_KT.asPbjKey(), CUSTOM_PAYER_ACCOUNT_KT.asPbjKey()));
    }

    @Test
    void tokenUpdateTokenWithMissingNewAutoRenewAccount() {
        final var txn = txnFrom(TOKEN_UPDATE_WITH_MISSING_AUTO_RENEW_ACCOUNT);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertTrue(context.failed());
        assertEquals(ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT, context.getStatus());
        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.getRequiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.getRequiredNonPayerKeys()), contains(TOKEN_ADMIN_KT.asPbjKey()));
    }
}
