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

package com.hedera.node.app.service.contract.impl.test.exec.scope;

import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.AN_ED25519_KEY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CANONICAL_ALIAS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONTRACTS_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_LEDGER_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SOME_DURATION;
import static com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils.synthAccountCreationFromHapi;
import static com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils.synthContractCreationFromParent;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations;
import com.hedera.node.app.service.contract.impl.state.WritableContractStateStore;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Collections;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleHederaOperationsTest {
    @Mock
    private HandleContext.SavepointStack savepointStack;

    @Mock
    private TokenServiceApi tokenServiceApi;

    @Mock
    private BlockRecordInfo blockRecordInfo;

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private HandleContext context;

    @Mock
    private WritableContractStateStore stateStore;

    @Mock
    private CryptoCreateRecordBuilder cryptoCreateRecordBuilder;

    @Mock
    private TinybarValues tinybarValues;

    private HandleHederaOperations subject;

    @BeforeEach
    void setUp() {
        subject = new HandleHederaOperations(DEFAULT_LEDGER_CONFIG, DEFAULT_CONTRACTS_CONFIG, context, tinybarValues);
    }

    @Test
    void returnsContextualStore() {
        given(context.writableStore(WritableContractStateStore.class)).willReturn(stateStore);

        assertSame(stateStore, subject.getStore());
    }

    @Test
    void usesExpectedLimit() {
        assertEquals(DEFAULT_CONTRACTS_CONFIG.maxNumber(), subject.contractCreationLimit());
    }

    @Test
    void delegatesEntropyToBlockRecordInfo() {
        final var pretendEntropy = Bytes.fromHex("0123456789");
        given(context.blockRecordInfo()).willReturn(blockRecordInfo);
        given(blockRecordInfo.getNMinus3RunningHash()).willReturn(pretendEntropy);
        assertSame(pretendEntropy, subject.entropy());
    }

    @Test
    void returnsZeroEntropyIfNMinus3HashMissing() {
        given(context.blockRecordInfo()).willReturn(blockRecordInfo);
        assertSame(HandleHederaOperations.ZERO_ENTROPY, subject.entropy());
    }

    @Test
    void createsNewSavepointWhenBeginningScope() {
        given(context.savepointStack()).willReturn(savepointStack);

        final var nestedScope = subject.begin();

        assertSame(subject, nestedScope);
        verify(savepointStack).createSavepoint();
    }

    @Test
    void rollsBackSavepointWhenReverting() {
        given(context.savepointStack()).willReturn(savepointStack);

        subject.revert();

        verify(savepointStack).rollback();
    }

    @Test
    void peekNumberUsesContext() {
        given(context.peekAtNewEntityNum()).willReturn(123L);
        assertEquals(123L, subject.peekNextEntityNumber());
    }

    @Test
    void useNumberUsesContext() {
        given(context.newEntityNum()).willReturn(123L);
        assertEquals(123L, subject.useNextEntityNumber());
    }

    @Test
    void commitIsNoopUntilSavepointExposesIt() {
        given(context.savepointStack()).willReturn(savepointStack);

        subject.commit();

        verify(savepointStack).commit();
    }

    @Test
    void lazyCreationCostInGasHardcoded() {
        assertEquals(1L, subject.lazyCreationCostInGas());
    }

    @Test
    void gasPriceInTinybarsDelegates() {
        given(tinybarValues.serviceGasPrice()).willReturn(1234L);
        assertEquals(1234L, subject.gasPriceInTinybars());
    }

    @Test
    void valueInTinybarsDelegates() {
        given(tinybarValues.asTinybars(1L)).willReturn(2L);
        assertEquals(2L, subject.valueInTinybars(1L));
    }

    @Test
    void collectFeeStillTransfersAllToNetworkFunding() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);

        subject.collectFee(TestHelpers.NON_SYSTEM_ACCOUNT_ID, 123L);

        verify(tokenServiceApi)
                .transferFromTo(
                        TestHelpers.NON_SYSTEM_ACCOUNT_ID,
                        AccountID.newBuilder()
                                .accountNum(DEFAULT_LEDGER_CONFIG.fundingAccount())
                                .build(),
                        123L);
    }

    @Test
    void refundFeeStillTransfersAllFromNetworkFunding() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);

        subject.refundFee(TestHelpers.NON_SYSTEM_ACCOUNT_ID, 123L);

        verify(tokenServiceApi)
                .transferFromTo(
                        AccountID.newBuilder()
                                .accountNum(DEFAULT_LEDGER_CONFIG.fundingAccount())
                                .build(),
                        TestHelpers.NON_SYSTEM_ACCOUNT_ID,
                        123L);
    }

    @Test
    void chargeStorageRentIsNoop() {
        assertDoesNotThrow(() -> subject.chargeStorageRent(1L, 2L, true));
    }

    @Test
    void updateStorageMetadataUsesApi() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);

        subject.updateStorageMetadata(NON_SYSTEM_ACCOUNT_ID.accountNumOrThrow(), Bytes.EMPTY, 2);

        verify(tokenServiceApi).updateStorageMetadata(NON_SYSTEM_ACCOUNT_ID, Bytes.EMPTY, 2);
    }

    @Test
    void createContractWithParentDispatchesAsExpectedThenMarksCreated() {
        final var parent = Account.newBuilder()
                .key(Key.newBuilder().contractID(ContractID.newBuilder().contractNum(123L)))
                .autoRenewAccountId(NON_SYSTEM_ACCOUNT_ID)
                .stakedNodeId(3)
                .declineReward(true)
                .autoRenewSeconds(666L)
                .maxAutoAssociations(321)
                .memo("Something")
                .build();
        final var pendingId = ContractID.newBuilder().contractNum(666L).build();
        final var synthContractCreation = synthContractCreationFromParent(pendingId, parent);
        final var synthAccountCreation =
                synthAccountCreationFromHapi(pendingId, CANONICAL_ALIAS, synthContractCreation);
        final var synthTxn = TransactionBody.newBuilder()
                .cryptoCreateAccount(synthAccountCreation)
                .build();
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);
        given(context.payer()).willReturn(A_NEW_ACCOUNT_ID);
        given(context.dispatchChildTransaction(
                        eq(synthTxn), eq(CryptoCreateRecordBuilder.class), any(Predicate.class), eq(A_NEW_ACCOUNT_ID)))
                .willReturn(cryptoCreateRecordBuilder);
        given(cryptoCreateRecordBuilder.status()).willReturn(OK);
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(accountStore.getAccountById(NON_SYSTEM_ACCOUNT_ID)).willReturn(parent);
        given(context.payer()).willReturn(A_NEW_ACCOUNT_ID);

        subject.createContract(666L, NON_SYSTEM_ACCOUNT_ID.accountNumOrThrow(), CANONICAL_ALIAS);

        verify(context)
                .dispatchChildTransaction(
                        eq(synthTxn), eq(CryptoCreateRecordBuilder.class), any(Predicate.class), eq(A_NEW_ACCOUNT_ID));
        verify(tokenServiceApi)
                .markAsContract(AccountID.newBuilder().accountNum(666L).build(), null);
    }

    @Test
    void createContractWithBodyDispatchesThenMarksAsContract() {
        final var someBody = ContractCreateTransactionBody.newBuilder()
                .adminKey(AN_ED25519_KEY)
                .autoRenewAccountId(NON_SYSTEM_ACCOUNT_ID)
                .autoRenewPeriod(SOME_DURATION)
                .build();
        final var pendingId = ContractID.newBuilder().contractNum(666L).build();
        final var synthTxn = TransactionBody.newBuilder()
                .cryptoCreateAccount(synthAccountCreationFromHapi(pendingId, CANONICAL_ALIAS, someBody))
                .build();
        given(context.payer()).willReturn(A_NEW_ACCOUNT_ID);
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);
        given(context.dispatchChildTransaction(
                        eq(synthTxn), eq(CryptoCreateRecordBuilder.class), any(Predicate.class), eq(A_NEW_ACCOUNT_ID)))
                .willReturn(cryptoCreateRecordBuilder);
        given(cryptoCreateRecordBuilder.status()).willReturn(OK);
        given(context.payer()).willReturn(A_NEW_ACCOUNT_ID);

        subject.createContract(666L, someBody, CANONICAL_ALIAS);

        verify(context)
                .dispatchChildTransaction(
                        eq(synthTxn), eq(CryptoCreateRecordBuilder.class), any(Predicate.class), eq(A_NEW_ACCOUNT_ID));
        verify(tokenServiceApi)
                .markAsContract(AccountID.newBuilder().accountNum(666L).build(), NON_SYSTEM_ACCOUNT_ID);
    }

    @Test
    void createContractWithFailedDispatchNotImplemented() {
        final var someBody = ContractCreateTransactionBody.newBuilder()
                .adminKey(AN_ED25519_KEY)
                .autoRenewAccountId(NON_SYSTEM_ACCOUNT_ID)
                .autoRenewPeriod(SOME_DURATION)
                .build();
        final var pendingId = ContractID.newBuilder().contractNum(666L).build();
        final var synthTxn = TransactionBody.newBuilder()
                .cryptoCreateAccount(synthAccountCreationFromHapi(pendingId, CANONICAL_ALIAS, someBody))
                .build();
        given(context.payer()).willReturn(A_NEW_ACCOUNT_ID);
        given(context.dispatchChildTransaction(
                        eq(synthTxn), eq(CryptoCreateRecordBuilder.class), any(Predicate.class), eq(A_NEW_ACCOUNT_ID)))
                .willReturn(cryptoCreateRecordBuilder);
        given(cryptoCreateRecordBuilder.status()).willReturn(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);

        assertThrows(AssertionError.class, () -> subject.createContract(666L, someBody, CANONICAL_ALIAS));
    }

    @Test
    void deleteUnaliasedContractUsesApi() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);
        subject.deleteUnaliasedContract(CALLED_CONTRACT_ID.contractNumOrThrow());
        verify(tokenServiceApi).deleteAndMaybeUnaliasContract(CALLED_CONTRACT_ID);
    }

    @Test
    void deleteAliasedContractUsesApi() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);
        subject.deleteAliasedContract(CANONICAL_ALIAS);
        verify(tokenServiceApi)
                .deleteAndMaybeUnaliasContract(
                        ContractID.newBuilder().evmAddress(CANONICAL_ALIAS).build());
    }

    @Test
    void getModifiedAccountNumbersIsNotActuallyNeeded() {
        assertSame(Collections.emptyList(), subject.getModifiedAccountNumbers());
    }

    @Test
    void getOriginalSlotsUsedDelegatesToApi() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);
        given(tokenServiceApi.originalKvUsageFor(A_NEW_ACCOUNT_ID)).willReturn(123L);
        assertEquals(123L, subject.getOriginalSlotsUsed(A_NEW_ACCOUNT_ID.accountNumOrThrow()));
    }
}
