///*
// * Copyright (C) 2023 Hedera Hashgraph, LLC
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.hedera.node.app.service.token.impl.test.handlers;
//
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_ALLOWANCE_SPENDER_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_FROM_IMMUTABLE_SENDER_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_MISSING_ACCOUNT_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NFT_FROM_IMMUTABLE_SENDER_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NFT_FROM_MISSING_SENDER_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NFT_TO_IMMUTABLE_RECEIVER_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NFT_TO_MISSING_RECEIVER_ALIAS_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NO_RECEIVER_SIG_USING_ALIAS_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_RECEIVER_IS_MISSING_ALIAS_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_RECEIVER_SIG_USING_ALIAS_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_SENDER_IS_MISSING_ALIAS_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_TOKEN_RECEIVER_IS_MISSING_ALIAS_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_TOKEN_TO_IMMUTABLE_RECEIVER_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_TO_IMMUTABLE_RECEIVER_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.NFT_TRANSFER_ALLOWANCE_SPENDER_SCENARIO;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_MOVING_HBARS_WITH_EXTANT_SENDER;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_MOVING_HBARS_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDER;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_EXTANT_SENDERS;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_MISSING_SENDERS;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_RECEIVER;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_SENDER;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_FT;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_HBAR;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_MISSING_TOKEN;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_BUT_ROYALTY_FEE_WITH_FALLBACK_TRIGGERED;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_SIG_REQ_WITH_FALLBACK_TRIGGERED_BUT_SENDER_IS_TREASURY;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_SIG_REQ_WITH_FALLBACK_WHEN_RECEIVER_IS_TREASURY;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_RECEIVER_SIG_REQ;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_USING_ALIAS;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDERS;
//import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSFER_ALLOWANCE_SPENDER_SCENARIO;
//import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER_KT;
//import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
//import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_KT;
//import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG_KT;
//import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SECOND_TOKEN_SENDER_KT;
//import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
//import static com.hedera.test.utils.KeyUtils.sanityRestored;
//import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_IS_IMMUTABLE;
//import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
//import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
//import static org.hamcrest.MatcherAssert.assertThat;
//import static org.hamcrest.Matchers.contains;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
//import com.hedera.node.app.spi.workflows.PreHandleContext;
//import com.hedera.hapi.node.base.Key;
//import com.hedera.hapi.node.base.ResponseCodeEnum;
//import org.junit.jupiter.api.Test;
//
//class CryptoTransferHandlerParityTest extends ParityTestBase {
//    private final CryptoTransferHandler subject = new CryptoTransferHandler();
//
//    @Test
//    void cryptoTransferTokenReceiverIsMissingAliasScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_TOKEN_RECEIVER_IS_MISSING_ALIAS_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertTrue(context.getRequiredNonPayerKeys().isEmpty());
//    }
//
//    @Test
//    void cryptoTransferReceiverIsMissingAliasScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_RECEIVER_IS_MISSING_ALIAS_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()),
//                contains(FIRST_TOKEN_SENDER_KT.asKey()));
//    }
//
//    @Test
//    void tokenTransactWithOwnershipChangeNoSigReqWithFallbackWhenReceiverIsTreasury() {
//        final var theTxn =
//                txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_SIG_REQ_WITH_FALLBACK_WHEN_RECEIVER_IS_TREASURY);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(NO_RECEIVER_SIG_KT.asKey()));
//    }
//
//    @Test
//    void cryptoTransferSenderIsMissingAliasScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_SENDER_IS_MISSING_ALIAS_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertContextFailedWithReqPayerKeyAnd(context, INVALID_ACCOUNT_ID);
//    }
//
//    @Test
//    void cryptoTransferNoReceiverSigUsingAliasScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_NO_RECEIVER_SIG_USING_ALIAS_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertTrue(context.getRequiredNonPayerKeys().isEmpty());
//    }
//
//    @Test
//    void cryptoTransferToImmutableReceiverScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_TO_IMMUTABLE_RECEIVER_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
//    }
//
//    @Test
//    void cryptoTransferTokenToImmutableReceiverScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_TOKEN_TO_IMMUTABLE_RECEIVER_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        // THEN
//        //        assertMetaFailedWith(meta, INVALID_ACCOUNT_ID);
//        // NOW
//        assertContextFailedWithReqPayerKeyAnd(context, ACCOUNT_IS_IMMUTABLE);
//    }
//
//    @Test
//    void cryptoTransferNftFromMissingSenderScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_NFT_FROM_MISSING_SENDER_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        // THEN
//        //        assertMetaFailedWith(meta, ACCOUNT_ID_DOES_NOT_EXIST);
//        // NOW
//        assertContextFailedWithReqPayerKeyAnd(context, INVALID_ACCOUNT_ID);
//    }
//
//    @Test
//    void cryptoTransferNftToMissingReceiverAliasScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_NFT_TO_MISSING_RECEIVER_ALIAS_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()),
//                contains(FIRST_TOKEN_SENDER_KT.asKey()));
//    }
//
//    @Test
//    void cryptoTransferNftFromImmutableSenderScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_NFT_FROM_IMMUTABLE_SENDER_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        // THEN
//        //        assertMetaFailedWith(meta, INVALID_ACCOUNT_ID);
//        // NOW
//        assertContextFailedWithReqPayerKeyAnd(context, ACCOUNT_IS_IMMUTABLE);
//    }
//
//    @Test
//    void cryptoTransferNftToImmutableReceiverScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_NFT_TO_IMMUTABLE_RECEIVER_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        // THEN
//        //        assertMetaFailedWith(meta, INVALID_ACCOUNT_ID);
//        // NOW
//        assertContextFailedWithReqPayerKeyAnd(context, ACCOUNT_IS_IMMUTABLE, FIRST_TOKEN_SENDER_KT.asKey());
//    }
//
//    @Test
//    void cryptoTransferFromImmutableSenderScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_FROM_IMMUTABLE_SENDER_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        // THEN
//        //        assertMetaFailedWith(meta, INVALID_ACCOUNT_ID);
//        // NOW
//        assertContextFailedWithReqPayerKeyAnd(context, ACCOUNT_IS_IMMUTABLE);
//    }
//
//    @Test
//    void cryptoTransferNoReceiverSigScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//    }
//
//    @Test
//    void cryptoTransferReceiverSigScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()),
//                contains(RECEIVER_SIG_KT.asKey()));
//    }
//
//    @Test
//    void cryptoTransferReceiverSigUsingAliasScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_RECEIVER_SIG_USING_ALIAS_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()),
//                contains(RECEIVER_SIG_KT.asKey()));
//    }
//
//    @Test
//    void cryptoTransferMissingAccountScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_MISSING_ACCOUNT_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertContextFailedWithReqPayerKeyAnd(context, INVALID_ACCOUNT_ID);
//    }
//
//    @Test
//    void tokenTransactWithExtantSenders() {
//        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_EXTANT_SENDERS);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(SECOND_TOKEN_SENDER_KT.asKey()));
//    }
//
//    @Test
//    void tokenTransactMovingHbarsWithExtantSender() {
//        final var theTxn = txnFrom(TOKEN_TRANSACT_MOVING_HBARS_WITH_EXTANT_SENDER);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
//    }
//
//    @Test
//    void tokenTransactMovingHbarsWithReceiverSigReqAndExtantSender() {
//        final var theTxn = txnFrom(TOKEN_TRANSACT_MOVING_HBARS_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDER);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(
//                sanityRestored(context.getRequiredNonPayerKeys()),
//                contains(FIRST_TOKEN_SENDER_KT.asKey(), RECEIVER_SIG_KT.asKey()));
//    }
//
//    @Test
//    void tokenTransactWithReceiverSigReqAndExtantSenders() {
//        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDERS);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(
//                sanityRestored(context.getRequiredNonPayerKeys()),
//                contains(FIRST_TOKEN_SENDER_KT.asKey(),
//                        SECOND_TOKEN_SENDER_KT.asKey(), RECEIVER_SIG_KT.asKey()));
//    }
//
//    @Test
//    void tokenTransactWithMissingSenders() {
//        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_MISSING_SENDERS);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertContextFailedWithReqPayerKeyAnd(context, INVALID_ACCOUNT_ID,
//                FIRST_TOKEN_SENDER_KT.asKey());
//    }
//
//    @Test
//    void tokenTransactWithOwnershipChange() {
//        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
//    }
//
//    @Test
//    void tokenTransactWithOwnershipChangeUsingAlias() {
//        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_USING_ALIAS);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
//    }
//
//    @Test
//    void tokenTransactWithOwnershipChangeReceiverSigReq() {
//        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_RECEIVER_SIG_REQ);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(
//                sanityRestored(context.getRequiredNonPayerKeys()),
//                contains(FIRST_TOKEN_SENDER_KT.asKey(), RECEIVER_SIG_KT.asKey(), SECOND_TOKEN_SENDER_KT.asKey()));
//    }
//
//    @Test
//    void tokenTransactWithOwnershipChangeNoReceiverSigReq() {
//        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
//    }
//
//    @Test
//    void tokenTransactWithOwnershipChangeNoReceiverSigReqButRoyaltyFeeWithFallbackTriggered() {
//        final var theTxn = txnFrom(
//                TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_BUT_ROYALTY_FEE_WITH_FALLBACK_TRIGGERED);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(
//                sanityRestored(context.getRequiredNonPayerKeys()),
//                contains(FIRST_TOKEN_SENDER_KT.asKey(), NO_RECEIVER_SIG_KT.asKey(), FIRST_TOKEN_SENDER_KT.asKey()));
//    }
//
//    @Test
//    void tokenTransactWithOwnershipChangeNoSigReqWithFallbackTriggeredButSenderIsTreasury() {
//        final var theTxn =
//                txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_SIG_REQ_WITH_FALLBACK_TRIGGERED_BUT_SENDER_IS_TREASURY);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(MISC_ACCOUNT_KT.asKey()));
//    }
//
//    @Test
//    void tokenTransactWithOwnershipChangeNoReceiverSigReqAndFallbackNotTriggeredDueToHbar() {
//        final var theTxn = txnFrom(
//                TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_HBAR);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
//    }
//
//    @Test
//    void tokenTransactWithOwnershipChangeNoReceiverSigReqAndFallbackNotTriggeredDueToFt() {
//        final var theTxn =
//                txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_FT);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
//    }
//
//    @Test
//    void tokenTransactWithOwnershipChangeNoReceiverSigReqAndMissingToken() {
//        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_MISSING_TOKEN);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertContextFailedWithReqPayerKeyAnd(context, INVALID_TOKEN_ID);
//    }
//
//    @Test
//    void tokenTransactWithOwnershipChangeMissingSender() {
//        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_SENDER);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertContextFailedWithReqPayerKeyAnd(context, INVALID_ACCOUNT_ID);
//    }
//
//    @Test
//    void tokenTransactWithOwnershipChangeMissingReceiver() {
//        final var theTxn = txnFrom(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_RECEIVER);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertContextFailedWithReqPayerKeyAnd(context, INVALID_ACCOUNT_ID, FIRST_TOKEN_SENDER_KT.asKey());
//    }
//
//    @Test
//    void cryptoTransferAllowanceSpenderScenario() {
//        final var theTxn = txnFrom(CRYPTO_TRANSFER_ALLOWANCE_SPENDER_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertTrue(context.getRequiredNonPayerKeys().isEmpty());
//    }
//
//    @Test
//    void tokenTransferAllowanceSpenderScenario() {
//        final var theTxn = txnFrom(TOKEN_TRANSFER_ALLOWANCE_SPENDER_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertTrue(context.getRequiredNonPayerKeys().isEmpty());
//    }
//
//    @Test
//    void nftTransferAllowanceSpenderScenario() {
//        final var theTxn = txnFrom(NFT_TRANSFER_ALLOWANCE_SPENDER_SCENARIO);
//        final var context = new PreHandleContext(readableAccountStore, theTxn);
//        subject.preHandle(context, readableAccountStore, readableTokenStore);
//
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertTrue(context.getRequiredNonPayerKeys().isEmpty());
//    }
//
//    private void assertContextFailedWithReqPayerKeyAnd(
//            final PreHandleContext context, final ResponseCodeEnum expectedFailure) {
//        assertTrue(context.failed());
//        assertEquals(expectedFailure, context.getStatus());
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertTrue(context.getRequiredNonPayerKeys().isEmpty());
//    }
//
//    private void assertContextFailedWithReqPayerKeyAnd(
//            final PreHandleContext context, final ResponseCodeEnum expectedFailure, final Key aNonPayerKey) {
//        assertTrue(context.failed());
//        assertEquals(expectedFailure, context.getStatus());
//        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
//        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(aNonPayerKey));
//    }
//}
