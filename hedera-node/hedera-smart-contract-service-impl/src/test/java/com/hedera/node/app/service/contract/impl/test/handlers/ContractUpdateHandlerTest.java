/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_MAX_AUTO_ASSOCIATIONS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hedera.hapi.util.HapiUtils.EMPTY_KEY_LIST;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.AN_ED25519_KEY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertFailsWith;
import static com.hedera.node.app.service.token.api.AccountSummariesApi.SENTINEL_ACCOUNT_ID;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractUpdateTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Account.Builder;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.handlers.ContractUpdateHandler;
import com.hedera.node.app.service.contract.impl.records.ContractUpdateStreamBuilder;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeCalculatorFactory;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.data.TokensConfig;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractUpdateHandlerTest extends ContractHandlerTestBase {

    private final TransactionID transactionID = TransactionID.newBuilder()
            .accountID(payer)
            .transactionValidStart(consensusTimestamp)
            .build();

    @Mock
    private HandleContext context;

    @Mock
    private StoreFactory storeFactory;

    @Mock
    private Account contract;

    @Mock
    private AttributeValidator attributeValidator;

    @Mock
    private ExpiryValidator expiryValidator;

    @Mock
    private TokenServiceApi tokenServiceApi;

    @Mock
    private Configuration configuration;

    @Mock
    private StakingConfig stakingConfig;

    @Mock
    private LedgerConfig ledgerConfig;

    @Mock
    private EntitiesConfig entitiesConfig;

    @Mock
    private TokensConfig tokensConfig;

    @Mock
    private ContractUpdateStreamBuilder recordBuilder;

    @Mock
    private HandleContext.SavepointStack stack;

    private ContractUpdateHandler subject;

    @BeforeEach
    public void setUp() {
        subject = new ContractUpdateHandler();
    }

    @Test
    void sigRequiredWithoutKeyFails() throws PreCheckException {
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder())
                .transactionID(transactionID)
                .build();

        assertThrowsPreCheck(() -> subject.pureChecks(txn), INVALID_CONTRACT_ID);
    }

    @Test
    void invalidAutoRenewAccountIdFails() throws PreCheckException {
        when(payerAccount.keyOrThrow()).thenReturn(AN_ED25519_KEY);
        when(accountStore.getContractById(targetContract)).thenReturn(payerAccount);

        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(
                        ContractUpdateTransactionBody.newBuilder()
                                .contractID(targetContract)
                                .autoRenewAccountId(asAccount("0.0.11111")) // invalid account
                        )
                .transactionID(transactionID)
                .build();
        final var context = new FakePreHandleContext(accountStore, txn);

        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_AUTORENEW_ACCOUNT);
    }

    @Test
    void callsKey2xIfAdminKeyRequired() throws PreCheckException {
        when(payerAccount.keyOrThrow()).thenReturn(AN_ED25519_KEY);
        when(accountStore.getContractById(targetContract)).thenReturn(payerAccount);

        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(
                        ContractUpdateTransactionBody.newBuilder()
                                .contractID(targetContract)
                                .memo("new memo") // invalid account
                        )
                .transactionID(transactionID)
                .build();
        final var context = new FakePreHandleContext(accountStore, txn);

        subject.preHandle(context);

        verify(payerAccount, times(2)).key();
    }

    @Test
    void callsKey1xIfAdminKeyNotRequired() throws PreCheckException {
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(
                        ContractUpdateTransactionBody.newBuilder()
                                .contractID(targetContract)
                                .expirationTime(Timestamp.newBuilder().seconds(1L)) // invalid account
                        )
                .transactionID(transactionID)
                .build();
        final var context = new FakePreHandleContext(accountStore, txn);

        subject.preHandle(context);

        verify(payerAccount, times(1)).key();
    }

    @Test
    void handleWithNullContextFails() {
        final HandleContext context = null;
        assertThrows(NullPointerException.class, () -> subject.handle(context));
    }

    @Test
    void handleWithNullContractUpdateTransactionBodyFails() {
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance((ContractUpdateTransactionBody) null)
                .transactionID(transactionID)
                .build();
        when(context.body()).thenReturn(txn);

        assertThrows(NullPointerException.class, () -> subject.handle(context));
    }

    @Test
    void handleWithNoContractIdFails() {
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(
                        ContractUpdateTransactionBody.newBuilder().contractID((ContractID) null))
                .transactionID(transactionID)
                .build();
        when(context.body()).thenReturn(txn);

        assertThrows(NullPointerException.class, () -> subject.handle(context));
    }

    @Test
    void handleWithNonExistingContractIdFails() {
        given(context.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(
                        ContractUpdateTransactionBody.newBuilder().contractID(targetContract))
                .transactionID(transactionID)
                .build();
        when(context.body()).thenReturn(txn);

        assertFailsWith(INVALID_CONTRACT_ID, () -> subject.handle(context));
    }

    @Test
    void handleWithInvalidKeyFails() {
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(Key.newBuilder()))
                .transactionID(transactionID)
                .build();

        assertThrowsPreCheck(() -> subject.pureChecks(txn), INVALID_ADMIN_KEY);
    }

    @Test
    void invalidContractIdKeyFails() {
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(Key.newBuilder().contractID(ContractID.DEFAULT)))
                .transactionID(transactionID)
                .build();

        assertThrowsPreCheck(() -> subject.pureChecks(txn), INVALID_ADMIN_KEY);
    }

    @Test
    void handleWithAValidContractIdKeyFails() {
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(Key.newBuilder()
                                .contractID(ContractID.newBuilder().contractNum(100))))
                .transactionID(transactionID)
                .build();

        assertThrowsPreCheck(() -> subject.pureChecks(txn), INVALID_ADMIN_KEY);
    }

    @Test
    void handleWithInvalidExpirationTimeAndExpiredAndPendingRemovalTrueFails() {
        final var expirationTime = 1L;

        when(accountStore.getContractById(targetContract)).thenReturn(contract);
        doReturn(attributeValidator).when(context).attributeValidator();
        doThrow(HandleException.class).when(attributeValidator).validateExpiry(expirationTime);
        when(contract.expiredAndPendingRemoval()).thenReturn(true);

        given(context.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(adminKey)
                        .expirationTime(Timestamp.newBuilder().seconds(expirationTime)))
                .transactionID(transactionID)
                .build();

        when(context.body()).thenReturn(txn);

        assertFailsWith(CONTRACT_EXPIRED_AND_PENDING_REMOVAL, () -> subject.handle(context));
    }

    @Test
    void handleModifyImmutableContract() {
        when(accountStore.getContractById(targetContract)).thenReturn(contract);

        given(context.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(adminKey))
                .transactionID(transactionID)
                .build();

        when(context.body()).thenReturn(txn);

        assertFailsWith(MODIFYING_IMMUTABLE_CONTRACT, () -> subject.handle(context));
    }

    @Test
    void handleWithExpirationTimeLesserThenExpirationSecondsFails() {
        final var expirationTime = 1L;

        doReturn(attributeValidator).when(context).attributeValidator();
        when(accountStore.getContractById(targetContract)).thenReturn(contract);
        when(contract.key()).thenReturn(Key.newBuilder().build());
        when(contract.expirationSecond()).thenReturn(expirationTime + 1);

        given(context.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(adminKey)
                        .memo("memo")
                        .expirationTime(Timestamp.newBuilder().seconds(expirationTime)))
                .transactionID(transactionID)
                .build();

        when(context.body()).thenReturn(txn);

        assertFailsWith(EXPIRATION_REDUCTION_NOT_ALLOWED, () -> subject.handle(context));
    }

    @Test
    void maxAutomaticTokenAssociationsBiggerThenAllowedFails() {
        final var maxAutomaticTokenAssociations = 10;

        when(configuration.getConfigData(LedgerConfig.class)).thenReturn(ledgerConfig);
        when(configuration.getConfigData(EntitiesConfig.class)).thenReturn(entitiesConfig);
        when(ledgerConfig.maxAutoAssociations()).thenReturn(maxAutomaticTokenAssociations - 1);
        when(entitiesConfig.unlimitedAutoAssociationsEnabled()).thenReturn(true);
        when(context.configuration()).thenReturn(configuration);

        when(accountStore.getContractById(targetContract)).thenReturn(contract);
        when(contract.key()).thenReturn(Key.newBuilder().build());

        given(context.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(adminKey)
                        .memo("memo")
                        .maxAutomaticTokenAssociations(maxAutomaticTokenAssociations))
                .transactionID(transactionID)
                .build();

        when(context.body()).thenReturn(txn);

        assertFailsWith(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT, () -> subject.handle(context));
    }

    @Test
    void maxAutomaticTokenAssociationsNegativeWithDisabledFlag() {
        when(configuration.getConfigData(LedgerConfig.class)).thenReturn(ledgerConfig);
        when(configuration.getConfigData(EntitiesConfig.class)).thenReturn(entitiesConfig);
        when(entitiesConfig.unlimitedAutoAssociationsEnabled()).thenReturn(false);
        when(context.configuration()).thenReturn(configuration);

        when(accountStore.getContractById(targetContract)).thenReturn(contract);
        when(contract.key()).thenReturn(Key.newBuilder().build());

        given(context.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(adminKey)
                        .memo("memo")
                        .maxAutomaticTokenAssociations(-1))
                .transactionID(transactionID)
                .build();

        when(context.body()).thenReturn(txn);

        assertFailsWith(INVALID_MAX_AUTO_ASSOCIATIONS, () -> subject.handle(context));
    }

    @Test
    void maxAutomaticTokenAssociationsNegativeWithEnabledFlag() {
        when(configuration.getConfigData(LedgerConfig.class)).thenReturn(ledgerConfig);
        when(configuration.getConfigData(EntitiesConfig.class)).thenReturn(entitiesConfig);
        when(entitiesConfig.unlimitedAutoAssociationsEnabled()).thenReturn(true);
        when(context.configuration()).thenReturn(configuration);

        when(accountStore.getContractById(targetContract)).thenReturn(contract);
        when(contract.key()).thenReturn(Key.newBuilder().build());

        given(context.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(adminKey)
                        .memo("memo")
                        .maxAutomaticTokenAssociations(-5))
                .transactionID(transactionID)
                .build();

        when(context.body()).thenReturn(txn);

        assertFailsWith(INVALID_MAX_AUTO_ASSOCIATIONS, () -> subject.handle(context));
    }

    @Test
    void maxAutomaticTokenAssociationsSmallerThenContractLimitFails() {
        final var maxAutomaticTokenAssociations = 10;

        when(configuration.getConfigData(LedgerConfig.class)).thenReturn(ledgerConfig);
        when(configuration.getConfigData(EntitiesConfig.class)).thenReturn(entitiesConfig);
        when(ledgerConfig.maxAutoAssociations()).thenReturn(maxAutomaticTokenAssociations + 1);
        when(entitiesConfig.unlimitedAutoAssociationsEnabled()).thenReturn(true);
        when(context.configuration()).thenReturn(configuration);

        when(accountStore.getContractById(targetContract)).thenReturn(contract);
        when(contract.maxAutoAssociations()).thenReturn(maxAutomaticTokenAssociations + 1);
        when(contract.key()).thenReturn(Key.newBuilder().build());

        given(context.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(adminKey)
                        .memo("memo")
                        .maxAutomaticTokenAssociations(maxAutomaticTokenAssociations))
                .transactionID(transactionID)
                .build();

        when(context.body()).thenReturn(txn);

        assertFailsWith(EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT, () -> subject.handle(context));
    }

    @Test
    void maxAutomaticTokenAssociationsBiggerThenMaxConfigFails() {
        final var maxAutomaticTokenAssociations = 10;

        when(configuration.getConfigData(LedgerConfig.class)).thenReturn(ledgerConfig);
        when(configuration.getConfigData(EntitiesConfig.class)).thenReturn(entitiesConfig);
        when(configuration.getConfigData(TokensConfig.class)).thenReturn(tokensConfig);
        when(ledgerConfig.maxAutoAssociations()).thenReturn(maxAutomaticTokenAssociations + 1);
        when(entitiesConfig.limitTokenAssociations()).thenReturn(true);
        when(tokensConfig.maxPerAccount()).thenReturn(maxAutomaticTokenAssociations - 1);
        when(context.configuration()).thenReturn(configuration);

        when(contract.maxAutoAssociations()).thenReturn(maxAutomaticTokenAssociations - 1);
        when(contract.key()).thenReturn(Key.newBuilder().build());
        when(accountStore.getContractById(targetContract)).thenReturn(contract);
        given(context.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(adminKey)
                        .memo("memo")
                        .maxAutomaticTokenAssociations(maxAutomaticTokenAssociations))
                .transactionID(transactionID)
                .build();

        when(context.body()).thenReturn(txn);

        assertFailsWith(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT, () -> subject.handle(context));
    }

    @Test
    void verifyTheCorrectOutsideValidatorsAndUpdateContractAPIAreCalled() {
        doReturn(attributeValidator).when(context).attributeValidator();
        when(accountStore.getContractById(targetContract)).thenReturn(contract);
        when(contract.accountIdOrThrow())
                .thenReturn(AccountID.newBuilder().accountNum(666).build());
        when(contract.key()).thenReturn(Key.newBuilder().build());
        when(context.expiryValidator()).thenReturn(expiryValidator);
        given(context.storeFactory()).willReturn(storeFactory);
        when(storeFactory.serviceApi(TokenServiceApi.class)).thenReturn(tokenServiceApi);
        given(storeFactory.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(adminKey)
                        .memo("memo"))
                .transactionID(transactionID)
                .build();
        when(context.body()).thenReturn(txn);
        when(context.configuration()).thenReturn(configuration);
        when(configuration.getConfigData(StakingConfig.class)).thenReturn(stakingConfig);
        when(stakingConfig.isEnabled()).thenReturn(true);
        when(contract.copyBuilder()).thenReturn(mock(Builder.class));
        when(context.savepointStack()).thenReturn(stack);
        when(stack.getBaseBuilder(ContractUpdateStreamBuilder.class)).thenReturn(recordBuilder);

        subject.handle(context);

        verify(expiryValidator, times(1)).resolveUpdateAttempt(any(), any(), anyBoolean());
        verify(tokenServiceApi, times(1))
                .assertValidStakingElectionForUpdate(anyBoolean(), anyBoolean(), any(), any(), any(), any(), any());
        verify(tokenServiceApi, times(1)).updateContract(any());
        verify(recordBuilder, times(1)).contractID(any());
    }

    @Test
    void adminKeyUpdated() {
        final var contract = Account.newBuilder().build();
        final var op = ContractUpdateTransactionBody.newBuilder()
                .adminKey(A_COMPLEX_KEY)
                .build();

        final var updatedContract = subject.update(contract, context, op);

        assertEquals(op.adminKey(), updatedContract.key());
    }

    @Test
    void adminKeyNotUpdatedWhenKeyIsEmpty() {
        final var contract = Account.newBuilder().key(A_COMPLEX_KEY).build();
        final var op = ContractUpdateTransactionBody.newBuilder()
                .adminKey(EMPTY_KEY_LIST)
                .build();

        final var updatedContract = subject.update(contract, context, op);

        assertEquals(contract.key(), updatedContract.key());
    }

    @Test
    void expirationTimeUpdated() {
        final var contract = Account.newBuilder().build();
        final var op = ContractUpdateTransactionBody.newBuilder()
                .expirationTime(Timestamp.newBuilder().seconds(10))
                .build();

        final var updatedContract = subject.update(contract, context, op);

        assertEquals(op.expirationTime().seconds(), updatedContract.expirationSecond());
        assertFalse(updatedContract.expiredAndPendingRemoval());
    }

    @Test
    void autoRenewSecondsUpdated() {
        final var contract = Account.newBuilder().build();
        final var op = ContractUpdateTransactionBody.newBuilder()
                .autoRenewPeriod(Duration.newBuilder().seconds(10))
                .build();

        final var updatedContract = subject.update(contract, context, op);

        assertEquals(op.autoRenewPeriod().seconds(), updatedContract.autoRenewSeconds());
    }

    @Test
    void memoUpdatedPassingMemoField() {
        when(context.attributeValidator()).thenReturn(attributeValidator);

        final var contract = Account.newBuilder().build();
        final var op = ContractUpdateTransactionBody.newBuilder().memo("memo").build();

        final var updatedContract = subject.update(contract, context, op);

        assertEquals(op.memo(), updatedContract.memo());
        verify(attributeValidator, times(1)).validateMemo(op.memo());
    }

    @Test
    void testCalculateFeesWithNoContractIDAndMemo() {
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder())
                .transactionID(transactionID)
                .build();
        final var feeCtx = mock(FeeContext.class);
        given(feeCtx.body()).willReturn(txn);
        given(feeCtx.readableStore(ReadableAccountStore.class)).willReturn(accountStore);

        final var feeCalcFactory = mock(FeeCalculatorFactory.class);
        final var feeCalc = mock(FeeCalculator.class);
        given(feeCtx.feeCalculatorFactory()).willReturn(feeCalcFactory);
        given(feeCalcFactory.feeCalculator(notNull())).willReturn(feeCalc);

        assertDoesNotThrow(() -> subject.calculateFees(feeCtx));
    }

    @Test
    void memoUpdatedPassingMemoWrapperField() {
        when(context.attributeValidator()).thenReturn(attributeValidator);

        final var contract = Account.newBuilder().build();
        final var op =
                ContractUpdateTransactionBody.newBuilder().memoWrapper("memo").build();

        final var updatedContract = subject.update(contract, context, op);

        assertEquals(op.memoWrapper(), updatedContract.memo());
        verify(attributeValidator, times(1)).validateMemo(op.memoWrapper());
    }

    @Test
    void stakedAccountIdUpdated() {
        final var contract = Account.newBuilder().build();
        final var op = ContractUpdateTransactionBody.newBuilder()
                .stakedAccountId(AccountID.newBuilder().accountNum(1))
                .build();

        final var updatedContract = subject.update(contract, context, op);

        assertEquals(op.stakedAccountId(), updatedContract.stakedAccountId());
    }

    @Test
    void stakedAccountIdWithSentinelAccountID() {
        final var contract = Account.newBuilder().build();
        final var op = ContractUpdateTransactionBody.newBuilder()
                .stakedAccountId(SENTINEL_ACCOUNT_ID)
                .build();

        final var updatedContract = subject.update(contract, context, op);

        assertNull(updatedContract.stakedAccountId());
    }

    @Test
    void stakedNodeIdUpdated() {
        final var contract = Account.newBuilder().build();
        final var op =
                ContractUpdateTransactionBody.newBuilder().stakedNodeId(10).build();

        final var updatedContract = subject.update(contract, context, op);

        assertEquals(op.stakedNodeId(), updatedContract.stakedNodeId());
    }

    @Test
    void declineRewardUpdated() {
        final var contract = Account.newBuilder().build();
        final var op =
                ContractUpdateTransactionBody.newBuilder().declineReward(true).build();

        final var updatedContract = subject.update(contract, context, op);

        assertTrue(updatedContract.declineReward());
    }

    @Test
    void autoRenewAccountIdUpdated() {
        final var contract = Account.newBuilder().build();
        final var op = ContractUpdateTransactionBody.newBuilder()
                .autoRenewAccountId(AccountID.newBuilder().accountNum(10))
                .build();

        final var updatedContract = subject.update(contract, context, op);

        assertEquals(op.autoRenewAccountId(), updatedContract.autoRenewAccountId());
    }

    @Test
    void maxAutomaticTokenAssociationsUpdated() {
        final var contract = Account.newBuilder().build();
        final var op = ContractUpdateTransactionBody.newBuilder()
                .maxAutomaticTokenAssociations(10)
                .build();

        final var updatedContract = subject.update(contract, context, op);

        assertEquals(op.maxAutomaticTokenAssociations(), updatedContract.maxAutoAssociations());
    }

    @Test
    void updateAllFields() {
        when(context.attributeValidator()).thenReturn(attributeValidator);

        final var contract = Account.newBuilder().build();
        final var op = ContractUpdateTransactionBody.newBuilder()
                .adminKey(A_COMPLEX_KEY)
                .expirationTime(Timestamp.newBuilder().seconds(10))
                .autoRenewPeriod(Duration.newBuilder().seconds(10))
                .memo("memo")
                .stakedAccountId(AccountID.newBuilder().accountNum(1))
                .stakedNodeId(10)
                .declineReward(true)
                .autoRenewAccountId(AccountID.newBuilder().accountNum(10))
                .maxAutomaticTokenAssociations(10)
                .build();

        final var updatedContract = subject.update(contract, context, op);

        assertEquals(op.adminKey(), updatedContract.key());
        assertEquals(op.expirationTime().seconds(), updatedContract.expirationSecond());
        assertFalse(updatedContract.expiredAndPendingRemoval());
        assertEquals(op.autoRenewPeriod().seconds(), updatedContract.autoRenewSeconds());
        assertEquals(op.memo(), updatedContract.memo());
        assertEquals(op.stakedAccountId(), updatedContract.stakedAccountId());
        assertEquals(op.stakedNodeId(), updatedContract.stakedNodeId());
        assertTrue(updatedContract.declineReward());
        assertEquals(op.autoRenewAccountId(), updatedContract.autoRenewAccountId());
        assertEquals(op.maxAutomaticTokenAssociations(), updatedContract.maxAutoAssociations());
        verify(attributeValidator, times(1)).validateMemo(op.memo());
    }

    @Test
    void handleWhenTargetIdContainOnlyEvmAddress() {
        doReturn(attributeValidator).when(context).attributeValidator();
        when(accountStore.getContractById(targetContractWithEvmAddress)).thenReturn(contract);
        when(contract.accountIdOrThrow())
                .thenReturn(AccountID.newBuilder().accountNum(999L).build());
        when(contract.key()).thenReturn(Key.newBuilder().build());
        when(context.expiryValidator()).thenReturn(expiryValidator);
        given(context.storeFactory()).willReturn(storeFactory);
        when(storeFactory.serviceApi(TokenServiceApi.class)).thenReturn(tokenServiceApi);
        given(storeFactory.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContractWithEvmAddress)
                        .adminKey(adminKey)
                        .memo("memo"))
                .transactionID(transactionID)
                .build();
        when(context.body()).thenReturn(txn);
        when(context.configuration()).thenReturn(configuration);
        when(configuration.getConfigData(StakingConfig.class)).thenReturn(stakingConfig);
        when(stakingConfig.isEnabled()).thenReturn(true);
        when(contract.copyBuilder()).thenReturn(mock(Builder.class));
        when(context.savepointStack()).thenReturn(stack);
        when(stack.getBaseBuilder(ContractUpdateStreamBuilder.class)).thenReturn(recordBuilder);

        subject.handle(context);

        verify(expiryValidator, times(1)).resolveUpdateAttempt(any(), any(), anyBoolean());
        verify(tokenServiceApi, times(1))
                .assertValidStakingElectionForUpdate(anyBoolean(), anyBoolean(), any(), any(), any(), any(), any());
        verify(tokenServiceApi, times(1)).updateContract(any());
        verify(recordBuilder, times(1)).contractID(any());
    }
}
