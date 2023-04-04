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

package com.hedera.node.app.service.mono.contracts.operation;

import static com.hedera.node.app.service.mono.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.ETHEREUM_NONCE;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.KEY;
import static com.hedera.node.app.service.mono.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.node.app.service.mono.txns.contract.ContractCreateTransitionLogic.STANDIN_CONTRACT_ID_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.accounts.ContractCustomizer;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.service.mono.utils.SidecarUtils;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaCreateOperationExternalizerTest {
    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private MessageFrame frame;

    @Mock
    private MessageFrame childFrame;

    @Mock
    private HederaStackedWorldStateUpdater updater;

    @Mock
    private EntityCreator creator;

    @Mock
    private RecordsHistorian recordsHistorian;

    @Mock
    private ContractCustomizer contractCustomizer;

    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private ContractAliases aliases;

    @Mock
    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;

    private static final ContractID lastAllocated = IdUtils.asContract("0.0.1234");

    private static final EntityId autoRenewId = new EntityId(0, 0, 8);

    private static final JKey nonEmptyKey = MiscUtils.asFcKeyUnchecked(Key.newBuilder()
            .setEd25519(ByteString.copyFrom("01234567890123456789012345678901".getBytes()))
            .build());
    private HederaCreateOperationExternalizer subject;

    static final Address PRETEND_CONTRACT_ADDRESS = Address.ALTBN128_ADD;

    @BeforeEach
    void setUp() {
        subject = new HederaCreateOperationExternalizer(
                creator, syntheticTxnFactory, recordsHistorian, dynamicProperties);
    }

    @Test
    void hasExpectedChildCompletionOnSuccessWithSidecarEnabled() {
        final var trackerCaptor = ArgumentCaptor.forClass(SideEffectsTracker.class);
        final var liveRecord = ExpirableTxnRecord.newBuilder()
                .setReceiptBuilder(TxnReceipt.newBuilder().setStatus(TxnReceipt.REVERTED_SUCCESS_LITERAL));
        final var mockCreation = TransactionBody.newBuilder()
                .setContractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .setAutoRenewAccountId(autoRenewId.toGrpcAccountId()));
        given(frame.getWorldUpdater()).willReturn(updater);
        givenUpdaterWithAliases(EntityIdUtils.parseAccount("0.0.1234"), nonEmptyKey);
        given(updater.customizerForPendingCreation()).willReturn(contractCustomizer);
        given(updater.idOfLastNewAddress()).willReturn(lastAllocated);
        given(syntheticTxnFactory.contractCreation(contractCustomizer)).willReturn(mockCreation);
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any())).willReturn(liveRecord);
        final var initCode = "initCode".getBytes();
        final var newContractMock = mock(Account.class);
        final var runtimeCode = "runtimeCode".getBytes();
        given(newContractMock.getCode()).willReturn(Bytes.of(runtimeCode));
        given(updater.get(PRETEND_CONTRACT_ADDRESS)).willReturn(newContractMock);
        final var sidecarRecord = TransactionSidecarRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(666L).build());
        final var sidecarUtilsMockedStatic = mockStatic(SidecarUtils.class);
        sidecarUtilsMockedStatic
                .when(() -> SidecarUtils.createContractBytecodeSidecarFrom(lastAllocated, initCode, runtimeCode))
                .thenReturn(sidecarRecord);
        given(dynamicProperties.enabledSidecars()).willReturn(Set.of(SidecarType.CONTRACT_BYTECODE));
        given(childFrame.getContractAddress()).willReturn(PRETEND_CONTRACT_ADDRESS);
        given(childFrame.getCode()).willReturn(CodeFactory.createCode(Bytes.wrap(initCode), 0, false));

        // when
        subject.externalize(frame, childFrame);

        // then:
        verify(creator)
                .createSuccessfulSyntheticRecord(eq(Collections.emptyList()), trackerCaptor.capture(), eq(EMPTY_MEMO));
        verify(updater).manageInProgressRecord(recordsHistorian, liveRecord, mockCreation, List.of(sidecarRecord));
        // and:
        final var tracker = trackerCaptor.getValue();
        assertTrue(tracker.hasTrackedContractCreation());
        assertEquals(lastAllocated, tracker.getTrackedNewContractId());
        assertArrayEquals(
                PRETEND_CONTRACT_ADDRESS.toArrayUnsafe(),
                tracker.getNewEntityAlias().toByteArray());
        // and:
        assertTrue(liveRecord.shouldNotBeExternalized());
        sidecarUtilsMockedStatic.close();
    }

    @Test
    void hasExpectedChildCompletionOnSuccessWithoutSidecarEnabled() {
        final var trackerCaptor = ArgumentCaptor.forClass(SideEffectsTracker.class);
        final var liveRecord = ExpirableTxnRecord.newBuilder()
                .setReceiptBuilder(TxnReceipt.newBuilder().setStatus(TxnReceipt.REVERTED_SUCCESS_LITERAL));
        final var mockCreation = TransactionBody.newBuilder()
                .setContractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .setAutoRenewAccountId(autoRenewId.toGrpcAccountId()));
        given(frame.getWorldUpdater()).willReturn(updater);
        givenUpdaterWithAliases(EntityIdUtils.parseAccount("0.0.1234"), nonEmptyKey);
        given(updater.customizerForPendingCreation()).willReturn(contractCustomizer);
        given(updater.idOfLastNewAddress()).willReturn(lastAllocated);
        given(syntheticTxnFactory.contractCreation(contractCustomizer)).willReturn(mockCreation);
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any())).willReturn(liveRecord);
        given(dynamicProperties.enabledSidecars()).willReturn(Set.of());
        given(childFrame.getContractAddress()).willReturn(PRETEND_CONTRACT_ADDRESS);

        // when:
        subject.externalize(frame, childFrame);

        // then:
        verify(creator)
                .createSuccessfulSyntheticRecord(eq(Collections.emptyList()), trackerCaptor.capture(), eq(EMPTY_MEMO));
        verify(updater).manageInProgressRecord(recordsHistorian, liveRecord, mockCreation, Collections.emptyList());
        // and:
        final var tracker = trackerCaptor.getValue();
        assertTrue(tracker.hasTrackedContractCreation());
        assertEquals(lastAllocated, tracker.getTrackedNewContractId());
        assertArrayEquals(
                PRETEND_CONTRACT_ADDRESS.toArrayUnsafe(),
                tracker.getNewEntityAlias().toByteArray());
        // and:
        assertTrue(liveRecord.shouldNotBeExternalized());
    }

    @Test
    void hasExpectedHollowAccountCompletionOnSuccessWithSidecarEnabled() {
        final var trackerCaptor = ArgumentCaptor.forClass(SideEffectsTracker.class);
        final var liveRecord = ExpirableTxnRecord.newBuilder()
                .setReceiptBuilder(TxnReceipt.newBuilder().setStatus(TxnReceipt.REVERTED_SUCCESS_LITERAL));
        final var mockCreation = TransactionBody.newBuilder()
                .setContractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .setAutoRenewAccountId(autoRenewId.toGrpcAccountId()));
        given(frame.getWorldUpdater()).willReturn(updater);
        final var hollowAccountId = EntityIdUtils.parseAccount("0.0.5678");
        givenUpdaterWithAliases(hollowAccountId, EMPTY_KEY);
        given(updater.customizerForPendingCreation()).willReturn(contractCustomizer);
        given(syntheticTxnFactory.contractCreation(contractCustomizer)).willReturn(mockCreation);
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any())).willReturn(liveRecord);
        final var initCode = "initCode".getBytes();
        final var newContractMock = mock(Account.class);
        final var runtimeCode = "runtimeCode".getBytes();
        given(newContractMock.getCode()).willReturn(Bytes.of(runtimeCode));
        given(updater.get(PRETEND_CONTRACT_ADDRESS)).willReturn(newContractMock);
        final var sidecarRecord = TransactionSidecarRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(666L).build());
        final var sidecarUtilsMockedStatic = mockStatic(SidecarUtils.class);
        sidecarUtilsMockedStatic
                .when(() -> SidecarUtils.createContractBytecodeSidecarFrom(
                        EntityIdUtils.asContract(hollowAccountId), initCode, runtimeCode))
                .thenReturn(sidecarRecord);
        given(dynamicProperties.enabledSidecars()).willReturn(Set.of(SidecarType.CONTRACT_BYTECODE));
        given(childFrame.getContractAddress()).willReturn(PRETEND_CONTRACT_ADDRESS);
        given(childFrame.getCode()).willReturn(CodeFactory.createCode(Bytes.wrap(initCode), 0, false));

        // when:
        subject.externalize(frame, childFrame);

        // then:
        verify(creator)
                .createSuccessfulSyntheticRecord(eq(Collections.emptyList()), trackerCaptor.capture(), eq(EMPTY_MEMO));
        verify(updater).reclaimLatestContractId();
        verify(updater.trackingAccounts()).set(hollowAccountId, IS_SMART_CONTRACT, true);
        verify(updater.trackingAccounts()).set(hollowAccountId, KEY, STANDIN_CONTRACT_ID_KEY);
        verify(updater.trackingAccounts()).set(hollowAccountId, ETHEREUM_NONCE, 1L);
        verify(updater).manageInProgressRecord(recordsHistorian, liveRecord, mockCreation, List.of(sidecarRecord));
        // and:
        final var tracker = trackerCaptor.getValue();
        assertTrue(tracker.hasTrackedContractCreation());
        assertEquals(EntityIdUtils.asContract(hollowAccountId), tracker.getTrackedNewContractId());
        assertArrayEquals(
                PRETEND_CONTRACT_ADDRESS.toArrayUnsafe(),
                tracker.getNewEntityAlias().toByteArray());
        // and:
        assertTrue(liveRecord.shouldNotBeExternalized());
        sidecarUtilsMockedStatic.close();
    }

    @Test
    void hasExpectedHollowAccountCompletionOnSuccessWithoutSidecarEnabled() {
        final var trackerCaptor = ArgumentCaptor.forClass(SideEffectsTracker.class);
        final var liveRecord = ExpirableTxnRecord.newBuilder()
                .setReceiptBuilder(TxnReceipt.newBuilder().setStatus(TxnReceipt.REVERTED_SUCCESS_LITERAL));
        final var mockCreation = TransactionBody.newBuilder()
                .setContractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .setAutoRenewAccountId(autoRenewId.toGrpcAccountId()));
        given(frame.getWorldUpdater()).willReturn(updater);
        final var hollowAccountId = EntityIdUtils.parseAccount("0.0.5678");
        givenUpdaterWithAliases(hollowAccountId, EMPTY_KEY);
        given(updater.customizerForPendingCreation()).willReturn(contractCustomizer);
        given(syntheticTxnFactory.contractCreation(contractCustomizer)).willReturn(mockCreation);
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any())).willReturn(liveRecord);
        given(dynamicProperties.enabledSidecars()).willReturn(Set.of());
        given(childFrame.getContractAddress()).willReturn(PRETEND_CONTRACT_ADDRESS);

        // when:
        subject.externalize(frame, childFrame);

        // then:
        verify(creator)
                .createSuccessfulSyntheticRecord(eq(Collections.emptyList()), trackerCaptor.capture(), eq(EMPTY_MEMO));
        verify(updater).reclaimLatestContractId();
        verify(updater.trackingAccounts()).set(hollowAccountId, IS_SMART_CONTRACT, true);
        verify(updater.trackingAccounts()).set(hollowAccountId, KEY, STANDIN_CONTRACT_ID_KEY);
        verify(updater.trackingAccounts()).set(hollowAccountId, ETHEREUM_NONCE, 1L);
        verify(updater).manageInProgressRecord(recordsHistorian, liveRecord, mockCreation, Collections.emptyList());
        // and:
        final var tracker = trackerCaptor.getValue();
        assertTrue(tracker.hasTrackedContractCreation());
        assertEquals(EntityIdUtils.asContract(hollowAccountId), tracker.getTrackedNewContractId());
        assertArrayEquals(
                PRETEND_CONTRACT_ADDRESS.toArrayUnsafe(),
                tracker.getNewEntityAlias().toByteArray());
        // and:
        assertTrue(liveRecord.shouldNotBeExternalized());
    }

    private void givenUpdaterWithAliases(final AccountID expectedAccountId, final JKey expectedKey) {
        given(updater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any())).willReturn(EntityIdUtils.asTypedEvmAddress(expectedAccountId));
        given(updater.trackingAccounts()).willReturn(accounts);
        given(accounts.contains(expectedAccountId)).willReturn((expectedKey != null));
        given(accounts.get(expectedAccountId, AccountProperty.KEY)).willReturn(expectedKey);
    }

    @Test
    void shouldFailBasedOnlazyCreationIsFalseWhenEnabledAndNoMatchingHollowAccount() {
        given(frame.getWorldUpdater()).willReturn(updater);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(false);
        final var hollowAccountId = EntityIdUtils.parseAccount("0.0.5678");
        given(updater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any())).willReturn(EntityIdUtils.asTypedEvmAddress(hollowAccountId));
        given(updater.trackingAccounts()).willReturn(accounts);
        given(accounts.contains(hollowAccountId)).willReturn(false);

        assertFalse(subject.shouldFailBasedOnLazyCreation(frame, PRETEND_CONTRACT_ADDRESS));
    }

    @Test
    void shouldFailBasedOnlazyCreationIsTrueWhenEnabledAndMatchingHollowAccount() {
        given(frame.getWorldUpdater()).willReturn(updater);
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(false);
        final var hollowAccountId = EntityIdUtils.parseAccount("0.0.5678");
        given(updater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any())).willReturn(EntityIdUtils.asTypedEvmAddress(hollowAccountId));
        given(updater.trackingAccounts()).willReturn(accounts);
        given(accounts.contains(hollowAccountId)).willReturn(true);
        given(accounts.get(any(), any())).willReturn(EMPTY_KEY);

        assertTrue(subject.shouldFailBasedOnLazyCreation(frame, PRETEND_CONTRACT_ADDRESS));
    }

    @Test
    void shouldFailBasedOnLazyCreationIsFalseWhenLazyCreationEnabled() {
        given(dynamicProperties.isLazyCreationEnabled()).willReturn(true);

        assertFalse(subject.shouldFailBasedOnLazyCreation(frame, PRETEND_CONTRACT_ADDRESS));
    }
}
