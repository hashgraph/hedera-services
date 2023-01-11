/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.state.logic;

import static com.hedera.node.app.service.mono.context.BasicTransactionContext.EMPTY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.sigs.Rationalization;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.stats.MiscSpeedometers;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.txns.span.EthTxExpansion;
import com.hedera.node.app.service.mono.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.RationalizedSigMeta;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class SigsAndPayerKeyScreenTest {
    @Mock private Rationalization rationalization;
    @Mock private PayerSigValidity payerSigValidity;
    @Mock private TransactionContext txnCtx;
    @Mock private MiscSpeedometers speedometers;
    @Mock private BiPredicate<JKey, TransactionSignature> validityTest;
    @Mock private PlatformTxnAccessor accessor;
    @Mock private Supplier<AccountStorageAdapter> accounts;
    @Mock private AccountStorageAdapter accountStorage;
    @Mock private MerkleAccount account;
    @Mock private EntityCreator creator;
    @Mock private ExpirableTxnRecord.Builder childRecordBuilder;
    @Mock private TxnReceipt.Builder txnReceiptBuilder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private ExpandHandleSpanMapAccessor spanMapAccessor;
    @Mock private AliasManager aliasManager;
    @Mock private GlobalDynamicProperties properties;
    @Mock private RationalizedSigMeta sigMeta;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private SigsAndPayerKeyScreen subject;

    @BeforeEach
    void setUp() {
        subject =
                new SigsAndPayerKeyScreen(
                        rationalization,
                        payerSigValidity,
                        txnCtx,
                        speedometers,
                        validityTest,
                        accounts,
                        creator,
                        syntheticTxnFactory,
                        sigImpactHistorian,
                        recordsHistorian,
                        spanMapAccessor,
                        aliasManager,
                        properties);
    }

    @Test
    void propagatesRationalizedStatus() {
        given(rationalization.finalStatus()).willReturn(INVALID_ACCOUNT_ID);
        given(accessor.getSigMeta()).willReturn(sigMeta);

        // when:
        final var result = subject.applyTo(accessor);

        // then:
        verify(rationalization).performFor(accessor);
        verifyNoInteractions(speedometers);
        // and:
        Assertions.assertEquals(INVALID_ACCOUNT_ID, result);
    }

    @Test
    void marksPayerSigActiveAndPreparesWhenVerified() {
        givenOkRationalization();
        given(accessor.getSigMeta()).willReturn(sigMeta);
        given(payerSigValidity.test(accessor, validityTest)).willReturn(true);

        // when:
        final var result = subject.applyTo(accessor);

        // then:
        verify(txnCtx).payerSigIsKnownActive();
        // and:
        Assertions.assertEquals(OK, result);
    }

    @Test
    void hollowAccountCompletionSucceeds() {
        givenOkRationalization();
        given(accessor.getSigMeta()).willReturn(sigMeta);
        given(payerSigValidity.test(accessor, validityTest)).willReturn(true);
        given(sigMeta.hasReplacedHollowKey()).willReturn(true);
        given(accounts.get()).willReturn(accountStorage);
        given(txnCtx.activePayer()).willReturn(AccountID.getDefaultInstance());
        given(accountStorage.getForModify(any())).willReturn(account);
        given(sigMeta.payerKey())
                .willReturn(
                        new JECDSASecp256k1Key(ByteString.copyFromUtf8("payerKey").toByteArray()));
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any()))
                .willReturn(childRecordBuilder);
        given(childRecordBuilder.getReceiptBuilder()).willReturn(txnReceiptBuilder);
        given(txnReceiptBuilder.getAccountId()).willReturn(EntityId.fromNum(1));

        // when:
        final var result = subject.applyTo(accessor);

        // then:
        verify(account).setAccountKey(sigMeta.payerKey());
        // and:
        Assertions.assertEquals(OK, result);
    }

    @Test
    void skipsHollowAccountCompletionForInvalidEthereumTxn() {
        givenOkRationalization();
        given(accessor.getSigMeta()).willReturn(sigMeta);
        given(properties.isLazyCreationEnabled()).willReturn(true);
        given(spanMapAccessor.getEthTxExpansion(accessor))
                .willReturn(new EthTxExpansion(null, INVALID_TRANSACTION));

        // when:
        final var result = subject.applyTo(accessor);

        // then:
        verify(account, never()).setAccountKey(any());
        // and:
        Assertions.assertEquals(OK, result);
    }

    @Test
    void skipsHollowAccountCompletionForEthereumTxnAliasNotFound() {
        givenOkRationalization();
        given(accessor.getSigMeta()).willReturn(sigMeta);
        given(properties.isLazyCreationEnabled()).willReturn(true);
        given(spanMapAccessor.getEthTxExpansion(accessor)).willReturn(new EthTxExpansion(null, OK));
        var key = new JECDSASecp256k1Key(ByteString.copyFromUtf8("publicKey").toByteArray());
        given(spanMapAccessor.getEthTxSigsMeta(accessor))
                .willReturn(new EthTxSigs(key.getECDSASecp256k1Key(), new byte[0]));
        given(aliasManager.lookupIdBy(any())).willReturn(EntityNum.MISSING_NUM);

        // when:
        final var result = subject.applyTo(accessor);

        // then:
        verify(account, never()).setAccountKey(any());
        // and:
        Assertions.assertEquals(OK, result);
    }

    @Test
    void skipsHollowAccountCompletionForEthereumTxnAccountKeyNotEmpty() {
        givenOkRationalization();
        given(accessor.getSigMeta()).willReturn(sigMeta);
        given(properties.isLazyCreationEnabled()).willReturn(true);
        given(spanMapAccessor.getEthTxExpansion(accessor)).willReturn(new EthTxExpansion(null, OK));
        var key = new JECDSASecp256k1Key(ByteString.copyFromUtf8("publicKey").toByteArray());
        given(spanMapAccessor.getEthTxSigsMeta(accessor))
                .willReturn(new EthTxSigs(key.getECDSASecp256k1Key(), new byte[0]));
        given(aliasManager.lookupIdBy(any())).willReturn(EntityNum.fromInt(1));
        given(accounts.get()).willReturn(accountStorage);
        given(accountStorage.get(any())).willReturn(account);
        given(account.getAccountKey())
                .willReturn(new JEd25519Key(ByteString.copyFromUtf8("accountKey").toByteArray()));

        // when:
        final var result = subject.applyTo(accessor);

        // then:
        verify(account, never()).setAccountKey(any());
        // and:
        Assertions.assertEquals(OK, result);
    }

    @Test
    void hollowAccountCompletionForEthereumTxnSucceeds() {
        givenOkRationalization();
        given(accessor.getSigMeta()).willReturn(sigMeta);
        given(properties.isLazyCreationEnabled()).willReturn(true);
        given(spanMapAccessor.getEthTxExpansion(accessor)).willReturn(new EthTxExpansion(null, OK));
        var key = new JECDSASecp256k1Key(ByteString.copyFromUtf8("publicKey").toByteArray());
        given(spanMapAccessor.getEthTxSigsMeta(accessor))
                .willReturn(new EthTxSigs(key.getECDSASecp256k1Key(), new byte[0]));
        given(aliasManager.lookupIdBy(any())).willReturn(EntityNum.fromInt(1));
        given(accounts.get()).willReturn(accountStorage);
        given(accountStorage.get(any())).willReturn(account);
        given(account.getAccountKey()).willReturn(EMPTY_KEY);
        given(accountStorage.getForModify(any())).willReturn(account);
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any()))
                .willReturn(childRecordBuilder);
        given(childRecordBuilder.getReceiptBuilder()).willReturn(txnReceiptBuilder);
        given(txnReceiptBuilder.getAccountId()).willReturn(EntityId.fromNum(1));

        // when:
        final var result = subject.applyTo(accessor);

        // then:
        verify(account).setAccountKey(key);
        // and:
        Assertions.assertEquals(OK, result);
    }

    @Test
    void warnsWhenPayerSigActivationThrows() {
        givenOkRationalization();
        given(accessor.getSigMeta()).willReturn(sigMeta);
        given(payerSigValidity.test(accessor, validityTest))
                .willThrow(IllegalArgumentException.class);

        // when:
        subject.applyTo(accessor);

        // then:
        assertThat(
                logCaptor.warnLogs(),
                contains(
                        Matchers.startsWith(
                                "Unhandled exception while testing payer sig activation")));
    }

    @Test
    void cyclesSyncWhenUsed() {
        givenOkRationalization(true);
        given(accessor.getSigMeta()).willReturn(sigMeta);

        // when:
        subject.applyTo(accessor);

        // then:
        verify(speedometers).cycleSyncVerifications();
    }

    @Test
    void doesntCyclesAsyncAnymore() {
        givenOkRationalization();
        given(accessor.getSigMeta()).willReturn(sigMeta);

        subject.applyTo(accessor);

        verifyNoInteractions(speedometers);
    }

    private void givenOkRationalization() {
        givenOkRationalization(false);
    }

    private void givenOkRationalization(boolean usedSync) {
        given(rationalization.finalStatus()).willReturn(OK);
        if (usedSync) {
            given(rationalization.usedSyncVerification()).willReturn(true);
        }
    }
}
