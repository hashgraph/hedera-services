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

package com.hedera.node.app.service.mono.txns.crypto;

import static com.hedera.node.app.service.mono.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.node.app.service.mono.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.txns.span.EthTxExpansion;
import com.hedera.node.app.service.mono.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.PendingCompletion;
import com.hedera.node.app.service.mono.utils.accessors.SwirldsTxnAccessor;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HollowAccountFinalizationLogicTest {

    @Mock
    private Supplier<AccountStorageAdapter> accountsSupplier;

    @Mock
    private EntityCreator creator;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private SigImpactHistorian sigImpactHistorian;

    @Mock
    private RecordsHistorian recordsHistorian;

    @Mock
    private ExpandHandleSpanMapAccessor spanMapAccessor;

    @Mock
    private AliasManager aliasManager;

    @Mock
    private GlobalDynamicProperties properties;

    @Mock
    private TransactionContext txnCtx;

    @Mock
    private AccountStorageAdapter accountStorageAdapter;

    @Mock
    private HederaAccount hederaAccount;

    @Mock
    private ExpirableTxnRecord.Builder expirableTxnRecordBuilder;

    @Mock
    private TxnReceipt.Builder txnReceiptBuilder;

    @Mock
    private TransactionBody.Builder txnBodyBuilder;

    @Mock
    private TxnAccessor txnAccessor;

    @Mock
    private SwirldsTxnAccessor swirldsTxnAccessor;

    private HollowAccountFinalizationLogic subject;

    @BeforeEach
    void setUp() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(swirldsTxnAccessor);

        subject = new HollowAccountFinalizationLogic(
                txnCtx,
                accountsSupplier,
                creator,
                syntheticTxnFactory,
                sigImpactHistorian,
                recordsHistorian,
                spanMapAccessor,
                aliasManager,
                properties);
    }

    @Test
    void doesNotPerformAnyFinalizationsOnEmptyPendingFinalizations() {
        final var result = subject.perform();

        assertEquals(OK, result);
        verifyNoInteractions(accountsSupplier);
        verifyNoInteractions(aliasManager);
        verifyNoInteractions(sigImpactHistorian);
        verifyNoInteractions(recordsHistorian);
    }

    @Test
    void finalizesHollowAccountPresentInPendingFinalizations() {
        final var keyBytes = "dksoadksoadksoadksoadksoadksoa123".getBytes();
        final var key = new JECDSASecp256k1Key(keyBytes);
        final var hollowNum = EntityNum.fromLong(5L);

        given(swirldsTxnAccessor.getPendingCompletions()).willReturn(List.of(new PendingCompletion(hollowNum, key)));

        given(accountsSupplier.get()).willReturn(accountStorageAdapter);
        given(accountStorageAdapter.getForModify(hollowNum)).willReturn(hederaAccount);
        given(hederaAccount.getEntityNum()).willReturn(hollowNum);
        given(properties.maxPrecedingRecords()).willReturn(5L);
        given(syntheticTxnFactory.updateHollowAccount(hollowNum, asKeyUnchecked(key)))
                .willReturn(txnBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any())).willReturn(expirableTxnRecordBuilder);
        given(expirableTxnRecordBuilder.getReceiptBuilder()).willReturn(txnReceiptBuilder);

        final var result = subject.perform();

        assertEquals(OK, result);
        verify(hederaAccount).setAccountKey(key);
        verify(creator).createSuccessfulSyntheticRecord(any(), any(), any());
        verify(sigImpactHistorian).markEntityChanged(hollowNum.longValue());
        final var evmAddress = EthSigsUtils.recoverAddressFromPubKey(keyBytes);
        verify(sigImpactHistorian).markAliasChanged(ByteString.copyFrom(evmAddress));
        verify(recordsHistorian)
                .trackPrecedingChildRecord(DEFAULT_SOURCE_ID, txnBodyBuilder, expirableTxnRecordBuilder);
        verify(expirableTxnRecordBuilder).getReceiptBuilder();
        verify(txnReceiptBuilder).nonRevertable();
    }

    @Test
    void finalizesWrappedEthereumSenderIfNeeded() {
        given(txnCtx.accessor()).willReturn(txnAccessor);
        given(spanMapAccessor.getEthTxExpansion(txnAccessor)).willReturn(new EthTxExpansion(null, OK));
        final var evmAddress = "evmAddress".getBytes();
        final var key = new JECDSASecp256k1Key(evmAddress);
        given(spanMapAccessor.getEthTxSigsMeta(txnAccessor))
                .willReturn(new EthTxSigs(key.getECDSASecp256k1Key(), evmAddress));
        final var hollowNum = EntityNum.fromLong(5L);
        given(aliasManager.lookupIdBy(ByteStringUtils.wrapUnsafely(evmAddress))).willReturn(hollowNum);
        given(accountsSupplier.get()).willReturn(accountStorageAdapter);
        given(accountStorageAdapter.getForModify(hollowNum)).willReturn(hederaAccount);
        given(accountStorageAdapter.get(hollowNum)).willReturn(hederaAccount);
        given(hederaAccount.getAccountKey()).willReturn(EMPTY_KEY);
        given(hederaAccount.getEntityNum()).willReturn(hollowNum);
        given(properties.maxPrecedingRecords()).willReturn(1L);
        given(syntheticTxnFactory.updateHollowAccount(hollowNum, asKeyUnchecked(key)))
                .willReturn(txnBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any())).willReturn(expirableTxnRecordBuilder);
        given(expirableTxnRecordBuilder.getReceiptBuilder()).willReturn(txnReceiptBuilder);

        final var result = subject.perform();

        assertEquals(OK, result);
        verify(hederaAccount).setAccountKey(key);
        verify(creator).createSuccessfulSyntheticRecord(any(), any(), any());
        verify(sigImpactHistorian).markEntityChanged(hollowNum.longValue());
        verify(recordsHistorian)
                .trackPrecedingChildRecord(DEFAULT_SOURCE_ID, txnBodyBuilder, expirableTxnRecordBuilder);
        verify(expirableTxnRecordBuilder).getReceiptBuilder();
        verify(txnReceiptBuilder).nonRevertable();
    }

    @Test
    void doesNotFinalizeWrappedSenderIfMissingEntity() {
        given(txnCtx.accessor()).willReturn(txnAccessor);
        given(spanMapAccessor.getEthTxExpansion(txnAccessor)).willReturn(new EthTxExpansion(null, OK));
        final var evmAddress = "evmAddress".getBytes();
        final var key = new JECDSASecp256k1Key(evmAddress);
        given(spanMapAccessor.getEthTxSigsMeta(txnAccessor))
                .willReturn(new EthTxSigs(key.getECDSASecp256k1Key(), evmAddress));
        given(aliasManager.lookupIdBy(ByteStringUtils.wrapUnsafely(evmAddress))).willReturn(EntityNum.MISSING_NUM);

        final var result = subject.perform();

        assertEquals(OK, result);
        verifyNoInteractions(creator);
        verifyNoInteractions(sigImpactHistorian);
        verifyNoInteractions(recordsHistorian);
    }

    @Test
    void doesNotFinalizeWrappedSenderIfNotHollow() {
        given(txnCtx.accessor()).willReturn(txnAccessor);
        given(spanMapAccessor.getEthTxExpansion(txnAccessor)).willReturn(new EthTxExpansion(null, OK));
        final var evmAddress = "evmAddress".getBytes();
        final var key = new JECDSASecp256k1Key(evmAddress);
        given(spanMapAccessor.getEthTxSigsMeta(txnAccessor))
                .willReturn(new EthTxSigs(key.getECDSASecp256k1Key(), evmAddress));
        final var hollowNum = EntityNum.fromLong(5L);
        given(aliasManager.lookupIdBy(ByteStringUtils.wrapUnsafely(evmAddress))).willReturn(hollowNum);
        given(accountsSupplier.get()).willReturn(accountStorageAdapter);
        given(accountStorageAdapter.get(hollowNum)).willReturn(hederaAccount);
        given(hederaAccount.getAccountKey()).willReturn(mock(JKey.class));

        final var result = subject.perform();

        assertEquals(OK, result);
        verifyNoInteractions(creator);
        verifyNoInteractions(sigImpactHistorian);
        verifyNoInteractions(recordsHistorian);
    }

    @Test
    void finalizesBothAccountsFromActiveMapAndWrappedEthereumSender() {
        final var keyBytes = "dksoa".getBytes();
        final var key = new JECDSASecp256k1Key(keyBytes);
        final var hollowNum = EntityNum.fromLong(5L);
        final var pendingCompletions = new ArrayList<PendingCompletion>();
        pendingCompletions.add(new PendingCompletion(hollowNum, key));

        given(swirldsTxnAccessor.getPendingCompletions()).willReturn(pendingCompletions);
        given(accountsSupplier.get()).willReturn(accountStorageAdapter);
        given(accountStorageAdapter.getForModify(hollowNum)).willReturn(hederaAccount);
        given(hederaAccount.getEntityNum()).willReturn(hollowNum);
        given(properties.maxPrecedingRecords()).willReturn(5L);
        given(syntheticTxnFactory.updateHollowAccount(hollowNum, asKeyUnchecked(key)))
                .willReturn(txnBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any())).willReturn(expirableTxnRecordBuilder);
        given(expirableTxnRecordBuilder.getReceiptBuilder()).willReturn(txnReceiptBuilder);


        given(txnCtx.accessor()).willReturn(txnAccessor);
        given(spanMapAccessor.getEthTxExpansion(txnAccessor)).willReturn(new EthTxExpansion(null, OK));
        final var evmAddress2 = "evmAddress2".getBytes();
        final var key2 = new JECDSASecp256k1Key(evmAddress2);
        given(spanMapAccessor.getEthTxSigsMeta(txnAccessor))
                .willReturn(new EthTxSigs(key2.getECDSASecp256k1Key(), evmAddress2));
        final var hollowNum2 = EntityNum.fromLong(6L);
        given(aliasManager.lookupIdBy(ByteStringUtils.wrapUnsafely(evmAddress2)))
                .willReturn(hollowNum2);
        final HederaAccount hollowAccount2 = mock(HederaAccount.class);
        given(accountStorageAdapter.getForModify(hollowNum2)).willReturn(hollowAccount2);
        given(accountStorageAdapter.get(hollowNum2)).willReturn(hollowAccount2);
        given(hollowAccount2.getAccountKey()).willReturn(EMPTY_KEY);
        given(hollowAccount2.getEntityNum()).willReturn(hollowNum2);
        given(syntheticTxnFactory.updateHollowAccount(hollowNum2, asKeyUnchecked(key2)))
                .willReturn(txnBodyBuilder);
        given(properties.maxPrecedingRecords()).willReturn(2L);

        final var result = subject.perform();

        assertEquals(OK, result);
        verify(hederaAccount).setAccountKey(key);
        verify(hollowAccount2).setAccountKey(key2);
        verify(creator, times(2)).createSuccessfulSyntheticRecord(any(), any(), any());
        verify(sigImpactHistorian).markEntityChanged(hollowNum.longValue());
        verify(sigImpactHistorian).markEntityChanged(hollowNum2.longValue());
        verify(recordsHistorian, times(2))
                .trackPrecedingChildRecord(DEFAULT_SOURCE_ID, txnBodyBuilder, expirableTxnRecordBuilder);
        verify(expirableTxnRecordBuilder, times(2)).getReceiptBuilder();
        verify(txnReceiptBuilder, times(2)).nonRevertable();
    }

    @Test
    void doesNotFinalizeEthSenderIfEthExpansionIsNotOk() {
        given(txnCtx.accessor()).willReturn(txnAccessor);
        given(spanMapAccessor.getEthTxExpansion(txnAccessor)).willReturn(new EthTxExpansion(null, INVALID_SIGNATURE));

        final var result = subject.perform();

        assertEquals(OK, result);

        verifyNoMoreInteractions(accountsSupplier);
        verifyNoMoreInteractions(aliasManager);
        verifyNoInteractions(sigImpactHistorian);
        verifyNoInteractions(recordsHistorian);
    }

    @Test
    void doesNotFinalizeAnyAccountsIfMaxPrecedingRecordsExceeded() {
        final var keyBytes = "dksoa".getBytes();
        final var key = new JECDSASecp256k1Key(keyBytes);
        final var evmAddress = "addres".getBytes();
        final var hollowNum = EntityNum.fromLong(5L);

        final var keyBytes2 = "dksoa".getBytes();
        final var key2 = new JECDSASecp256k1Key(keyBytes2);
        final var evmAddress2 = "addres".getBytes();
        final var hollowNum2 = EntityNum.fromLong(6L);

        given(swirldsTxnAccessor.getPendingCompletions())
                .willReturn(List.of(new PendingCompletion(hollowNum, key), new PendingCompletion(hollowNum2, key2)));
        given(properties.maxPrecedingRecords()).willReturn(1L);

        final var result = subject.perform();

        assertEquals(MAX_CHILD_RECORDS_EXCEEDED, result);

        verifyNoInteractions(accountsSupplier);
        verifyNoInteractions(aliasManager);
        verifyNoInteractions(sigImpactHistorian);
        verifyNoInteractions(recordsHistorian);
    }
}
