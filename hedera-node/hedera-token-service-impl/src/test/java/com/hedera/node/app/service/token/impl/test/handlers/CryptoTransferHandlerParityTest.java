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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_ALLOWANCE_SPENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_FROM_IMMUTABLE_SENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_MISSING_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NFT_FROM_IMMUTABLE_SENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NFT_FROM_MISSING_SENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NFT_TO_IMMUTABLE_RECEIVER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NFT_TO_MISSING_RECEIVER_ALIAS_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NO_RECEIVER_SIG_USING_ALIAS_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_RECEIVER_IS_MISSING_ALIAS_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_RECEIVER_SIG_USING_ALIAS_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_SENDER_IS_MISSING_ALIAS_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_TOKEN_RECEIVER_IS_MISSING_ALIAS_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_TOKEN_TO_IMMUTABLE_RECEIVER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_TO_IMMUTABLE_RECEIVER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.NFT_TRANSFER_ALLOWANCE_SPENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_MOVING_HBARS_WITH_EXTANT_SENDER;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_MOVING_HBARS_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDER;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_EXTANT_SENDERS;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_MISSING_SENDERS;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_RECEIVER;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_SENDER;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_FT;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_HBAR;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_BUT_ROYALTY_FEE_WITH_FALLBACK_TRIGGERED;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_SIG_REQ_WITH_FALLBACK_TRIGGERED_BUT_SENDER_IS_TREASURY;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_SIG_REQ_WITH_FALLBACK_WHEN_RECEIVER_IS_TREASURY;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_RECEIVER_SIG_REQ;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_USING_ALIAS;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDERS;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSFER_ALLOWANCE_SPENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SECOND_TOKEN_SENDER_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.ParityTestBase;
import com.hedera.node.app.service.token.impl.validators.CryptoTransferValidator;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.junit.jupiter.api.Test;

class CryptoTransferHandlerParityTest extends ParityTestBase {
    private final CryptoTransferValidator validator = new CryptoTransferValidator();
    private final CryptoTransferHandler subject = new CryptoTransferHandler(validator);

    @Test
    void cryptoTransferTokenReceiverIsMissingAliasScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_TOKEN_RECEIVER_IS_MISSING_ALIAS_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertTrue(context.requiredNonPayerKeys().isEmpty());
    }

    @Test
    void cryptoTransferReceiverIsMissingAliasScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_RECEIVER_IS_MISSING_ALIAS_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(FIRST_TOKEN_SENDER_KT.asPbjKey()));
    }

    @Test
    void tokenTransactWithOwnershipChangeNoSigReqWithFallbackWhenReceiverIsTreasury() throws PreCheckException {
        final var theTxn =
                txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_SIG_REQ_WITH_FALLBACK_WHEN_RECEIVER_IS_TREASURY);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertTrue(context.requiredNonPayerKeys().contains(NO_RECEIVER_SIG_KT.asPbjKey()));
    }

    @Test
    void cryptoTransferSenderIsMissingAliasScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_SENDER_IS_MISSING_ALIAS_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
    }

    @Test
    void cryptoTransferNoReceiverSigUsingAliasScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_NO_RECEIVER_SIG_USING_ALIAS_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertTrue(context.requiredNonPayerKeys().isEmpty());
    }

    @Test
    void cryptoTransferToImmutableReceiverScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_TO_IMMUTABLE_RECEIVER_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(FIRST_TOKEN_SENDER_KT.asPbjKey()));
    }

    @Test
    void cryptoTransferTokenToImmutableReceiverScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_TOKEN_TO_IMMUTABLE_RECEIVER_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
    }

    @Test
    void cryptoTransferNftFromMissingSenderScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_NFT_FROM_MISSING_SENDER_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
    }

    @Test
    void cryptoTransferNftToMissingReceiverAliasScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_NFT_TO_MISSING_RECEIVER_ALIAS_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(FIRST_TOKEN_SENDER_KT.asPbjKey()));
    }

    @Test
    void cryptoTransferNftFromImmutableSenderScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_NFT_FROM_IMMUTABLE_SENDER_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
    }

    @Test
    void cryptoTransferNftToImmutableReceiverScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_NFT_TO_IMMUTABLE_RECEIVER_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
    }

    @Test
    void cryptoTransferFromImmutableSenderScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_FROM_IMMUTABLE_SENDER_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
    }

    @Test
    void cryptoTransferNoReceiverSigScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
    }

    @Test
    void cryptoTransferReceiverSigScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(RECEIVER_SIG_KT.asPbjKey()));
    }

    @Test
    void cryptoTransferReceiverSigUsingAliasScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_RECEIVER_SIG_USING_ALIAS_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(RECEIVER_SIG_KT.asPbjKey()));
    }

    @Test
    void cryptoTransferMissingAccountScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_MISSING_ACCOUNT_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
    }

    @Test
    void tokenTransactWithExtantSenders() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_EXTANT_SENDERS);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(SECOND_TOKEN_SENDER_KT.asPbjKey()));
    }

    @Test
    void tokenTransactMovingHbarsWithExtantSender() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_TRANSACT_MOVING_HBARS_WITH_EXTANT_SENDER);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(FIRST_TOKEN_SENDER_KT.asPbjKey()));
    }

    @Test
    void tokenTransactMovingHbarsWithReceiverSigReqAndExtantSender() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_TRANSACT_MOVING_HBARS_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDER);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(FIRST_TOKEN_SENDER_KT.asPbjKey(), RECEIVER_SIG_KT.asPbjKey()));
    }

    @Test
    void tokenTransactWithReceiverSigReqAndExtantSenders() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDERS);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(
                        FIRST_TOKEN_SENDER_KT.asPbjKey(),
                        SECOND_TOKEN_SENDER_KT.asPbjKey(),
                        RECEIVER_SIG_KT.asPbjKey()));
    }

    @Test
    void tokenTransactWithMissingSenders() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_MISSING_SENDERS);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
    }

    @Test
    void tokenTransactWithOwnershipChange() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(FIRST_TOKEN_SENDER_KT.asPbjKey()));
    }

    @Test
    void tokenTransactWithOwnershipChangeUsingAlias() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_USING_ALIAS);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(FIRST_TOKEN_SENDER_KT.asPbjKey()));
    }

    @Test
    void tokenTransactWithOwnershipChangeReceiverSigReq() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_RECEIVER_SIG_REQ);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(
                        FIRST_TOKEN_SENDER_KT.asPbjKey(),
                        RECEIVER_SIG_KT.asPbjKey(),
                        SECOND_TOKEN_SENDER_KT.asPbjKey()));
    }

    @Test
    void tokenTransactWithOwnershipChangeNoReceiverSigReq() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys(), contains(FIRST_TOKEN_SENDER_KT.asPbjKey()));
    }

    @Test
    void tokenTransactWithOwnershipChangeNoReceiverSigReqButRoyaltyFeeWithFallbackTriggered() throws PreCheckException {
        final var theTxn = txnFrom(
                TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_BUT_ROYALTY_FEE_WITH_FALLBACK_TRIGGERED);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(FIRST_TOKEN_SENDER_KT.asPbjKey(), NO_RECEIVER_SIG_KT.asPbjKey()));
    }

    @Test
    void tokenTransactWithOwnershipChangeNoSigReqWithFallbackTriggeredButSenderIsTreasury() throws PreCheckException {
        final var theTxn =
                txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_SIG_REQ_WITH_FALLBACK_TRIGGERED_BUT_SENDER_IS_TREASURY);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertTrue(context.requiredNonPayerKeys().contains(MISC_ACCOUNT_KT.asPbjKey()));
    }

    @Test
    void tokenTransactWithOwnershipChangeNoReceiverSigReqAndFallbackNotTriggeredDueToHbar() throws PreCheckException {
        final var theTxn = txnFrom(
                TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_HBAR);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        // We don't want the NO_RECEIVER_SIG_KT to be included in the required keys because the account's receiver sig
        // required is false
        assertThat(
                context.requiredNonPayerKeys(),
                contains(FIRST_TOKEN_SENDER_KT.asPbjKey(), SECOND_TOKEN_SENDER_KT.asPbjKey()));
    }

    @Test
    void tokenTransactWithOwnershipChangeNoReceiverSigReqAndFallbackNotTriggeredDueToFt() throws PreCheckException {
        final var theTxn =
                txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_FT);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        // Again, we don't want NO_RECEIVER_SIG_KT in the required keys because receiver sig required is false
        assertThat(
                context.requiredNonPayerKeys(),
                containsInAnyOrder(FIRST_TOKEN_SENDER_KT.asPbjKey(), SECOND_TOKEN_SENDER_KT.asPbjKey()));
    }

    @Test
    void tokenTransactWithOwnershipChangeNoReceiverSigReqAndMissingToken() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_MISSING_TOKEN);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TOKEN_ID);
    }

    @Test
    void tokenTransactWithOwnershipChangeMissingSender() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_SENDER);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
    }

    @Test
    void tokenTransactWithOwnershipChangeMissingReceiver() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_RECEIVER);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
    }

    @Test
    void cryptoTransferAllowanceSpenderScenario() throws PreCheckException {
        final var theTxn = txnFrom(CRYPTO_TRANSFER_ALLOWANCE_SPENDER_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertTrue(context.requiredNonPayerKeys().isEmpty());
    }

    @Test
    void tokenTransferAllowanceSpenderScenario() throws PreCheckException {
        final var theTxn = txnFrom(TOKEN_TRANSFER_ALLOWANCE_SPENDER_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);
        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertTrue(context.requiredNonPayerKeys().isEmpty());
    }

    @Test
    void nftTransferAllowanceSpenderScenario() throws PreCheckException {
        final var theTxn = txnFrom(NFT_TRANSFER_ALLOWANCE_SPENDER_SCENARIO);
        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertTrue(context.requiredNonPayerKeys().isEmpty());
    }
}
