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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.CryptoCreateHandler;
import com.hedera.node.app.service.token.impl.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CryptoCreateHandler}.
 * Tests the following:
 * <ul>
 *     <li>preHandle works when there is a receiverSigRequired</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CryptoCreateHandlerTest extends CryptoHandlerTestBase {
    @Mock
    private HandleContext handleContext;

    private CryptoCreateHandler subject = new CryptoCreateHandler();
    private TransactionBody txn;
    private CryptoCreateRecordBuilder recordBuilder;

    private static final boolean CREATABLE_ACCOUNTS = true;
    private static final boolean NON_CREATABLE_ACCOUNTS = false;
    private static final long defaultInitialBalance = 100L;

    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshStoresWithCurrentTokenInWritable();
        txn = new CryptoCreateBuilder().build();
        recordBuilder = subject.newRecordBuilder();
    }

    @Test
    @DisplayName("preHandle works when there is a receiverSigRequired")
    void preHandleCryptoCreateVanilla() throws PreCheckException {
        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 1);
        assertEquals(key, context.payerKey());
    }

    @Test
    @DisplayName("preHandle fails when initial balance is not greater than zero")
    void preHandleFailsWhenInitialBalanceIsNegative() throws PreCheckException {
        txn = new CryptoCreateBuilder().withInitialBalance(-1L).build();
        final var context = new PreHandleContext(readableStore, txn);
        final var msg = assertThrows(PreCheckException.class, () -> subject.preHandle(context));

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 0);
        assertEquals(key, context.payerKey());
        assertEquals(INVALID_INITIAL_BALANCE, msg.responseCode());
    }

    @Test
    @DisplayName("preHandle fails without auto-renew period specified")
    void preHandleFailsWhenNoAutoRenewPeriodSpecified() throws PreCheckException {
        txn = new CryptoCreateBuilder().withNoAutoRenewPeriod().build();
        final var context = new PreHandleContext(readableStore, txn);
        final var msg = assertThrows(PreCheckException.class, () -> subject.preHandle(context));

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 0);
        assertEquals(key, context.payerKey());
        assertEquals(INVALID_RENEWAL_PERIOD, msg.responseCode());
    }

    @Test
    @DisplayName("preHandle fails when negative send record threshold is specified")
    void preHandleFailsWhenSendRecordThresholdIsNegative() throws PreCheckException {
        txn = new CryptoCreateBuilder().withSendRecordThreshold(-1).build();
        final var context = new PreHandleContext(readableStore, txn);
        final var msg = assertThrows(PreCheckException.class, () -> subject.preHandle(context));

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 0);
        assertEquals(key, context.payerKey());
        assertEquals(INVALID_SEND_RECORD_THRESHOLD, msg.responseCode());
    }

    @Test
    @DisplayName("preHandle fails when negative receive record threshold is specified")
    void preHandleFailsWhenReceiveRecordThresholdIsNegative() throws PreCheckException {
        txn = new CryptoCreateBuilder().withReceiveRecordThreshold(-1).build();
        final var context = new PreHandleContext(readableStore, txn);
        final var msg = assertThrows(PreCheckException.class, () -> subject.preHandle(context));

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 0);
        assertEquals(key, context.payerKey());
        assertEquals(INVALID_RECEIVE_RECORD_THRESHOLD, msg.responseCode());
    }

    @Test
    @DisplayName("preHandle fails when proxy accounts id is specified")
    void preHandleFailsWhenProxyAccountIdIsSpecified() throws PreCheckException {
        txn = new CryptoCreateBuilder().withProxyAccountNum(1).build();
        final var context = new PreHandleContext(readableStore, txn);
        final var msg = assertThrows(PreCheckException.class, () -> subject.preHandle(context));

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 0);
        assertEquals(key, context.payerKey());
        assertEquals(PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED, msg.responseCode());
    }

    @Test
    @DisplayName("preHandle succeeds when initial balance is zero")
    void preHandleWorksWhenInitialBalanceIsZero() throws PreCheckException {
        txn = new CryptoCreateBuilder().withInitialBalance(0L).build();
        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 1);
        assertEquals(key, context.payerKey());
    }

    @Test
    @DisplayName("preHandle works when there is no receiverSigRequired")
    void noReceiverSigRequiredPreHandleCryptoCreate() throws PreCheckException {
        final var noReceiverSigTxn =
                new CryptoCreateBuilder().withReceiverSigReq(false).build();
        final var expected = new PreHandleContext(readableStore, noReceiverSigTxn);

        final var context = new PreHandleContext(readableStore, noReceiverSigTxn);
        subject.preHandle(context);

        assertEquals(expected.body(), context.body());
        assertFalse(context.requiredNonPayerKeys().contains(key));
        basicMetaAssertions(context, 0);
        assertThat(context.requiredNonPayerKeys()).isEmpty();
        assertEquals(key, context.payerKey());
    }

    @Test
    @DisplayName("handle fails when account cannot be created if usage limit exceeded")
    void failsWhenAccountCannotBeCreated() {
        final var msg = assertThrows(
                HandleException.class,
                () -> subject.handle(
                        handleContext, txn, writableStore, subject.newRecordBuilder(), NON_CREATABLE_ACCOUNTS));
        assertThat(msg.getStatus()).isEqualTo(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
    }

    @Test
    @DisplayName("handle works when account can be created without any alias")
    void handleCryptoCreateVanilla() {
        given(handleContext.consensusNow()).willReturn(consensusInstant);
        given(handleContext.newEntityNumSupplier()).willReturn(() -> 1000L);

        // newly created account and payer account are not modified. Validate payers balance
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(1000L)));
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(id.accountNum())));
        assertEquals(payerBalance, writableStore.get(id).get().tinybarBalance());

        subject.handle(handleContext, txn, writableStore, recordBuilder, CREATABLE_ACCOUNTS);

        // newly created account and payer account are modified
        assertTrue(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(1000L)));
        assertTrue(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(id.accountNum())));

        // Validate created account exists and check record builder has created account recorded
        final var optionalAccount =
                writableStore.get(AccountID.newBuilder().accountNum(1000L).build());
        assertTrue(optionalAccount.isPresent());
        assertEquals(1000L, recordBuilder.getCreatedAccount());

        // validate fields on created account
        final var createdAccount = optionalAccount.get();

        assertTrue(createdAccount.receiverSigRequired());
        assertEquals(1000L, createdAccount.accountNumber());
        assertEquals(Bytes.EMPTY, createdAccount.alias());
        assertEquals(otherKey, createdAccount.key());
        assertEquals(consensusTimestamp.seconds() + defaultAutoRenewPeriod, createdAccount.expiry());
        assertEquals(defaultInitialBalance, createdAccount.tinybarBalance());
        assertEquals("Create Account", createdAccount.memo());
        assertFalse(createdAccount.deleted());
        assertEquals(0L, createdAccount.stakedToMe());
        assertEquals(0L, createdAccount.stakePeriodStart());
        // staked node id is stored in state as negative long
        assertEquals(-3 - 1, createdAccount.stakedNumber());
        assertFalse(createdAccount.declineReward());
        assertTrue(createdAccount.receiverSigRequired());
        assertEquals(0L, createdAccount.headTokenNumber());
        assertEquals(0L, createdAccount.headNftId());
        assertEquals(0L, createdAccount.headNftSerialNumber());
        assertEquals(0L, createdAccount.numberOwnedNfts());
        assertEquals(0, createdAccount.maxAutoAssociations());
        assertEquals(0, createdAccount.usedAutoAssociations());
        assertEquals(0, createdAccount.numberAssociations());
        assertFalse(createdAccount.smartContract());
        assertEquals(0, createdAccount.numberPositiveBalances());
        assertEquals(0L, createdAccount.ethereumNonce());
        assertEquals(0L, createdAccount.stakeAtStartOfLastRewardedPeriod());
        assertEquals(0L, createdAccount.autoRenewAccountNumber());
        assertEquals(defaultAutoRenewPeriod, createdAccount.autoRenewSecs());
        assertEquals(0, createdAccount.contractKvPairsNumber());
        assertTrue(createdAccount.cryptoAllowances().isEmpty());
        assertTrue(createdAccount.approveForAllNftAllowances().isEmpty());
        assertTrue(createdAccount.tokenAllowances().isEmpty());
        assertEquals(0, createdAccount.numberTreasuryTitles());
        assertFalse(createdAccount.expiredAndPendingRemoval());
        assertNull(createdAccount.firstContractStorageKey());

        // validate payer balance reduced
        assertEquals(9_900L, writableStore.get(id).get().tinybarBalance());
    }

    @Test
    @DisplayName("handle works when account can be created without any alias using staked account id")
    void handleCryptoCreateVanillaWithStakedAccountId() {
        txn = new CryptoCreateBuilder().withStakedAccountId(1000).build();
        given(handleContext.consensusNow()).willReturn(consensusInstant);
        given(handleContext.newEntityNumSupplier()).willReturn(() -> 1000L);

        // newly created account and payer account are not modified. Validate payers balance
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(1000L)));
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(id.accountNum())));
        assertEquals(payerBalance, writableStore.get(id).get().tinybarBalance());

        subject.handle(handleContext, txn, writableStore, recordBuilder, CREATABLE_ACCOUNTS);

        // newly created account and payer account are modified
        assertTrue(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(1000L)));
        assertTrue(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(id.accountNum())));

        // Validate created account exists and check record builder has created account recorded
        final var optionalAccount =
                writableStore.get(AccountID.newBuilder().accountNum(1000L).build());
        assertTrue(optionalAccount.isPresent());
        assertEquals(1000L, recordBuilder.getCreatedAccount());

        // validate fields on created account
        final var createdAccount = optionalAccount.get();

        assertTrue(createdAccount.receiverSigRequired());
        assertEquals(1000L, createdAccount.accountNumber());
        assertEquals(Bytes.EMPTY, createdAccount.alias());
        assertEquals(otherKey, createdAccount.key());
        assertEquals(consensusTimestamp.seconds() + defaultAutoRenewPeriod, createdAccount.expiry());
        assertEquals(defaultInitialBalance, createdAccount.tinybarBalance());
        assertEquals("Create Account", createdAccount.memo());
        assertFalse(createdAccount.deleted());
        assertEquals(0L, createdAccount.stakedToMe());
        assertEquals(0L, createdAccount.stakePeriodStart());
        // staked node id is stored in state as negative long
        assertEquals(1000L, createdAccount.stakedNumber());
        assertFalse(createdAccount.declineReward());
        assertTrue(createdAccount.receiverSigRequired());
        assertEquals(0L, createdAccount.headTokenNumber());
        assertEquals(0L, createdAccount.headNftId());
        assertEquals(0L, createdAccount.headNftSerialNumber());
        assertEquals(0L, createdAccount.numberOwnedNfts());
        assertEquals(0, createdAccount.maxAutoAssociations());
        assertEquals(0, createdAccount.usedAutoAssociations());
        assertEquals(0, createdAccount.numberAssociations());
        assertFalse(createdAccount.smartContract());
        assertEquals(0, createdAccount.numberPositiveBalances());
        assertEquals(0L, createdAccount.ethereumNonce());
        assertEquals(0L, createdAccount.stakeAtStartOfLastRewardedPeriod());
        assertEquals(0L, createdAccount.autoRenewAccountNumber());
        assertEquals(defaultAutoRenewPeriod, createdAccount.autoRenewSecs());
        assertEquals(0, createdAccount.contractKvPairsNumber());
        assertTrue(createdAccount.cryptoAllowances().isEmpty());
        assertTrue(createdAccount.approveForAllNftAllowances().isEmpty());
        assertTrue(createdAccount.tokenAllowances().isEmpty());
        assertEquals(0, createdAccount.numberTreasuryTitles());
        assertFalse(createdAccount.expiredAndPendingRemoval());
        assertNull(createdAccount.firstContractStorageKey());

        // validate payer balance reduced
        assertEquals(9_900L, writableStore.get(id).get().tinybarBalance());
    }

    @Test
    @DisplayName("handle fails when autoRenewPeriod is not set. This should not happen as there should"
            + " be a semantic check in `preHandle` and handle workflow should reject the "
            + "transaction before reaching handle")
    void handleFailsWhenAutoRenewPeriodNotSet() {
        txn = new CryptoCreateBuilder().withNoAutoRenewPeriod().build();
        // newly created account and payer account are not modified. Validate payers balance
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(1000L)));
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(id.accountNum())));
        assertEquals(payerBalance, writableStore.get(id).get().tinybarBalance());

        assertThrows(
                NullPointerException.class,
                () -> subject.handle(handleContext, txn, writableStore, recordBuilder, CREATABLE_ACCOUNTS));
    }

    @Test
    @DisplayName("handle fails when payer account can't pay for the newly created account initial balance")
    void handleFailsWhenPayerHasInsufficientBalance() {
        txn = new CryptoCreateBuilder().withInitialBalance(payerBalance + 1L).build();

        // newly created account and payer account are not modified. Validate payers balance
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(1000L)));
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(id.accountNum())));
        assertEquals(payerBalance, writableStore.get(id).get().tinybarBalance());

        final var msg = assertThrows(
                HandleException.class,
                () -> subject.handle(handleContext, txn, writableStore, recordBuilder, CREATABLE_ACCOUNTS));
        assertEquals(INSUFFICIENT_PAYER_BALANCE, msg.getStatus());

        final var recordMsg = assertThrows(IllegalStateException.class, () -> recordBuilder.getCreatedAccount());
        assertEquals("No new account number was recorded", recordMsg.getMessage());

        // newly created account and payer account are not modified
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(1000L)));
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(id.accountNum())));
    }

    @Test
    @DisplayName("handle fails when payer account is deleted")
    void handleFailsWhenPayerIsDeleted() {
        changeAccountToDeleted();

        final var msg = assertThrows(
                HandleException.class,
                () -> subject.handle(handleContext, txn, writableStore, recordBuilder, CREATABLE_ACCOUNTS));
        assertEquals(ACCOUNT_DELETED, msg.getStatus());

        final var recordMsg = assertThrows(IllegalStateException.class, () -> recordBuilder.getCreatedAccount());
        assertEquals("No new account number was recorded", recordMsg.getMessage());

        // newly created account and payer account are not modified
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(1000L)));
    }

    @Test
    @DisplayName("handle fails when payer account doesn't exist")
    void handleFailsWhenPayerInvalid() {
        txn = new CryptoCreateBuilder()
                .withPayer(AccountID.newBuilder().accountNum(600L).build())
                .build();

        final var msg = assertThrows(
                HandleException.class,
                () -> subject.handle(handleContext, txn, writableStore, recordBuilder, CREATABLE_ACCOUNTS));
        assertEquals(INVALID_PAYER_ACCOUNT_ID, msg.getStatus());

        final var recordMsg = assertThrows(IllegalStateException.class, () -> recordBuilder.getCreatedAccount());
        assertEquals("No new account number was recorded", recordMsg.getMessage());

        // newly created account and payer account are not modified
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(1000L)));
    }

    @Test
    @DisplayName("handle commits when any alias is mentioned in the transaction")
    void handleCommitsAnyAlias() {
        txn = new CryptoCreateBuilder().withAlias(Bytes.wrap("alias")).build();

        given(handleContext.consensusNow()).willReturn(consensusInstant);
        given(handleContext.newEntityNumSupplier()).willReturn(() -> 1000L);

        // newly created account and payer account are not modified. Validate payers balance
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(1000L)));
        assertFalse(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(id.accountNum())));
        assertEquals(payerBalance, writableStore.get(id).get().tinybarBalance());

        subject.handle(handleContext, txn, writableStore, recordBuilder, CREATABLE_ACCOUNTS);

        // newly created account and payer account are modified
        assertTrue(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(1000L)));
        assertTrue(writableStore.modifiedAccountsInState().contains(EntityNumVirtualKey.fromLong(id.accountNum())));
        assertEquals(
                Bytes.wrap("alias"),
                writableStore
                        .get(AccountID.newBuilder().accountNum(1000L).build())
                        .get()
                        .alias());
    }

    private void changeAccountToDeleted() {
        final var copy = account.copyBuilder().deleted(true).build();
        writableAccounts.put(EntityNumVirtualKey.fromLong(id.accountNum()), copy);
        given(writableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(writableAccounts);
        writableStore = new WritableAccountStore(writableStates);
    }

    /**
     * A builder for {@link TransactionBody} instances.
     */
    private class CryptoCreateBuilder {
        private AccountID payer = id;
        private long initialBalance = defaultInitialBalance;
        private long autoRenewPeriod = defaultAutoRenewPeriod;
        private boolean receiverSigReq = true;
        private Bytes alias = null;
        private long sendRecordThreshold = 0;
        private long receiveRecordThreshold = 0;
        private AccountID proxyAccountId = null;
        private long stakeNodeId = 3;
        private long stakedAccountId = 0;

        private CryptoCreateBuilder() {}

        public TransactionBody build() {
            final var transactionID =
                    TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
            final var createTxnBody = CryptoCreateTransactionBody.newBuilder()
                    .key(otherKey)
                    .receiverSigRequired(receiverSigReq)
                    .initialBalance(initialBalance)
                    .memo("Create Account")
                    .sendRecordThreshold(sendRecordThreshold)
                    .receiveRecordThreshold(receiveRecordThreshold);

            if (autoRenewPeriod > 0) {
                createTxnBody.autoRenewPeriod(
                        Duration.newBuilder().seconds(autoRenewPeriod).build());
            }
            if (alias != null) {
                createTxnBody.alias(alias);
            }
            if (proxyAccountId != null) {
                createTxnBody.proxyAccountID(proxyAccountId);
            }
            if (stakedAccountId > 0) {
                createTxnBody.stakedAccountId(
                        AccountID.newBuilder().accountNum(stakedAccountId).build());
            } else {
                createTxnBody.stakedNodeId(stakeNodeId);
            }

            return TransactionBody.newBuilder()
                    .transactionID(transactionID)
                    .cryptoCreateAccount(createTxnBody.build())
                    .build();
        }

        public CryptoCreateBuilder withPayer(final AccountID payer) {
            this.payer = payer;
            return this;
        }

        public CryptoCreateBuilder withInitialBalance(final long initialBalance) {
            this.initialBalance = initialBalance;
            return this;
        }

        public CryptoCreateBuilder withAutoRenewPeriod(final long autoRenewPeriod) {
            this.autoRenewPeriod = autoRenewPeriod;
            return this;
        }

        public CryptoCreateBuilder withProxyAccountNum(final long proxyAccountNum) {
            this.proxyAccountId =
                    AccountID.newBuilder().accountNum(proxyAccountNum).build();
            return this;
        }

        public CryptoCreateBuilder withSendRecordThreshold(final long threshold) {
            this.sendRecordThreshold = threshold;
            return this;
        }

        public CryptoCreateBuilder withReceiveRecordThreshold(final long threshold) {
            this.receiveRecordThreshold = threshold;
            return this;
        }

        public CryptoCreateBuilder withAlias(final Bytes alias) {
            this.alias = alias;
            return this;
        }

        public CryptoCreateBuilder withNoAutoRenewPeriod() {
            this.autoRenewPeriod = -1;
            return this;
        }

        public CryptoCreateBuilder withStakedAccountId(final long id) {
            this.stakedAccountId = id;
            return this;
        }

        public CryptoCreateBuilder withReceiverSigReq(final boolean receiverSigReq) {
            this.receiverSigReq = receiverSigReq;
            return this;
        }
    }
}
