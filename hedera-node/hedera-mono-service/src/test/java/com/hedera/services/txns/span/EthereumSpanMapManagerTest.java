/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.span;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.StateChildren;
import com.hedera.services.context.primitives.SignedStateViewFactory;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.MetadataMapFactory;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.sigs.order.LinkedRefs;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.txns.contract.ContractCallTransitionLogic;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.*;
import com.swirlds.virtualmap.VirtualMap;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EthereumSpanMapManagerTest {
    @Mock private EthTxData ethTxData;
    @Mock private EthTxSigs ethTxSigs;
    @Mock private LinkedRefs linkedRefs;
    @Mock private StateChildren stateChildren;
    @Mock private VirtualMap<VirtualBlobKey, VirtualBlobValue> blobs;
    @Mock private TxnAccessor accessor;
    @Mock private ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private CustomFeeSchedules customFeeSchedules;
    @Mock private AliasManager aliasManager;
    @Mock private SignedStateViewFactory stateViewFactory;
    @Mock private ContractCallTransitionLogic contractCallTransitionLogic;
    @Mock private Function<EthTxData, EthTxSigs> sigsFunction;
    @Mock private MutableStateChildren workingState;
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;

    private final Map<String, Object> spanMap = new HashMap<>();
    private final ExpandHandleSpanMapAccessor spanMapAccessor = new ExpandHandleSpanMapAccessor();

    private SpanMapManager subject;

    @BeforeEach
    void setUp() {
        subject =
                new SpanMapManager(
                        sigsFunction,
                        contractCallTransitionLogic,
                        new ExpandHandleSpanMapAccessor(),
                        impliedTransfersMarshal,
                        dynamicProperties,
                        stateViewFactory,
                        syntheticTxnFactory,
                        customFeeSchedules,
                        sigImpactHistorian,
                        workingState,
                        aliasManager);
    }

    @Test
    void canOnlyExpandEthSpanIfAccessorIsForEthTx() {
        given(accessor.getSpanMap()).willReturn(spanMap);
        given(stateViewFactory.childrenOfLatestSignedState())
                .willReturn(Optional.of(stateChildren));
        spanMapAccessor.setEthTxDataMeta(accessor, ethTxData);
        given(accessor.getFunction()).willReturn(ContractCall);

        assertThrows(IllegalArgumentException.class, () -> subject.expandEthereumSpan(accessor));
    }

    @Test
    void expansionIsNoopIfNoStateChildrenAvailable() {
        subject.expandEthereumSpan(accessor);

        assertNull(spanMapAccessor.getEthTxExpansion(accessor));
    }

    @Test
    void expansionIsNoopIfSpanMapIsImmutable() {
        given(accessor.getFunction()).willReturn(EthereumTransaction);
        given(accessor.getSpanMap()).willReturn(Map.of("ethTxDataMeta", ethTxData));
        txn = TransactionBody.newBuilder().setEthereumTransaction(bodyWithoutCallData).build();
        given(accessor.getTxn()).willReturn(txn);
        given(stateViewFactory.childrenOfLatestSignedState())
                .willReturn(Optional.of(stateChildren));
        given(stateChildren.signedAt()).willReturn(lastHandled);

        assertDoesNotThrow(() -> subject.expandEthereumSpan(accessor));
    }

    @Test
    void expansionIsFailureIfCallDataIsSetAndFileDoesntExist() {
        givenExpandableAccessor(bodyWithCallData);

        subject.expandEthereumSpan(accessor);
        expansion = spanMapAccessor.getEthTxExpansion(accessor);

        assertExpansionHasExpectedLinkRefsAnd(INVALID_FILE_ID);
    }

    @Test
    void expansionIsFailureIfCallDataIsSetAndFileIsDeleted() {
        givenExpandableAccessor(bodyWithCallData);
        given(blobs.get(metadataKey)).willReturn(asBlob(deletedMeta));

        subject.expandEthereumSpan(accessor);
        expansion = spanMapAccessor.getEthTxExpansion(accessor);

        assertExpansionHasExpectedLinkRefsAnd(FILE_DELETED);
    }

    @Test
    void expansionIsFailureIfCallDataIsSetAndFileIsEmpty() {
        givenExpandableAccessor(bodyWithCallData);
        given(blobs.get(metadataKey)).willReturn(asBlob(undeletedMeta));
        given(blobs.get(dataKey)).willReturn(new VirtualBlobValue(new byte[0]));

        subject.expandEthereumSpan(accessor);
        expansion = spanMapAccessor.getEthTxExpansion(accessor);

        assertExpansionHasExpectedLinkRefsAnd(CONTRACT_FILE_EMPTY);
    }

    @Test
    void expansionIsFailureIfCallDataIsSetAndFileIsNotDecodable() {
        givenExpandableAccessor(bodyWithCallData);
        given(blobs.get(metadataKey)).willReturn(asBlob(undeletedMeta));
        given(blobs.get(dataKey))
                .willReturn(
                        new VirtualBlobValue(new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff}));

        subject.expandEthereumSpan(accessor);
        expansion = spanMapAccessor.getEthTxExpansion(accessor);

        assertExpansionHasExpectedLinkRefsAnd(INVALID_FILE_ID);
    }

    @Test
    void expansionUpdatesEthTxCallDataIsSetAndFileIsNonEmpty() {
        givenExpandableAccessor(bodyWithCallData);
        given(blobs.get(metadataKey)).willReturn(asBlob(undeletedMeta));
        given(blobs.get(dataKey)).willReturn(dataValue);
        given(ethTxData.replaceCallData(unhexedCallData)).willReturn(ethTxData);
        given(syntheticTxnFactory.synthContractOpFromEth(ethTxData))
                .willReturn(Optional.of(synthCallBody));

        subject.expandEthereumSpan(accessor);
        expansion = spanMapAccessor.getEthTxExpansion(accessor);

        assertExpansionHasExpectedLinkRefsAnd(OK);
        verify(ethTxData).replaceCallData(unhexedCallData);
        verify(contractCallTransitionLogic)
                .preFetchOperation(ContractCallTransactionBody.getDefaultInstance());
    }

    @Test
    void expansionExtractsSignature() {
        givenUsableAccessor(bodyWithoutCallData);
        given(stateViewFactory.childrenOfLatestSignedState())
                .willReturn(Optional.of(stateChildren));
        given(sigsFunction.apply(ethTxData)).willReturn(ethTxSigs);

        subject.expandEthereumSpan(accessor);

        final var sigsMeta = spanMapAccessor.getEthTxSigsMeta(accessor);
        assertSame(ethTxSigs, sigsMeta);
    }

    @Test
    void expansionFailsIfSignatureCantBeExtracted() {
        givenUsableAccessor(bodyWithoutCallData);
        given(stateViewFactory.childrenOfLatestSignedState())
                .willReturn(Optional.of(stateChildren));
        given(sigsFunction.apply(ethTxData)).willThrow(IllegalArgumentException.class);

        subject.expandEthereumSpan(accessor);

        expansion = spanMapAccessor.getEthTxExpansion(accessor);
        assertEquals(INVALID_ETHEREUM_TRANSACTION, expansion.result());
    }

    @Test
    void expansionFailsIfHapiTxnCannotBeSynthesized() {
        givenUsableAccessor(bodyWithoutCallData);
        given(stateViewFactory.childrenOfLatestSignedState())
                .willReturn(Optional.of(stateChildren));
        given(sigsFunction.apply(ethTxData)).willReturn(ethTxSigs);

        subject.expandEthereumSpan(accessor);

        expansion = spanMapAccessor.getEthTxExpansion(accessor);
        assertEquals(INVALID_ETHEREUM_TRANSACTION, expansion.result());
    }

    @Test
    void expansionSetsSynthesizedHapiTxnInContext() {
        givenUsableAccessor(bodyWithoutCallData);
        given(stateViewFactory.childrenOfLatestSignedState())
                .willReturn(Optional.of(stateChildren));
        given(sigsFunction.apply(ethTxData)).willReturn(ethTxSigs);
        given(syntheticTxnFactory.synthContractOpFromEth(ethTxData))
                .willReturn(Optional.of(synthCreateBody));

        subject.expandEthereumSpan(accessor);

        final var expectedBody = synthCreateBody.build();
        assertEquals(expectedBody, spanMapAccessor.getEthTxBodyMeta(accessor));

        expansion = spanMapAccessor.getEthTxExpansion(accessor);
        assertEquals(OK, expansion.result());
    }

    @Test
    void rationalizationUpdatesEthTxCallDataIsSetAndFileIsNonEmpty() {
        givenRationalizableAccessor(bodyWithCallData);
        given(blobs.get(metadataKey)).willReturn(asBlob(undeletedMeta));
        given(blobs.get(dataKey)).willReturn(dataValue);
        given(ethTxData.replaceCallData(unhexedCallData)).willReturn(ethTxData);
        given(syntheticTxnFactory.synthContractOpFromEth(ethTxData))
                .willReturn(Optional.of(synthCallBody));
        willAnswer(
                        invocationOnMock -> {
                            final Map<String, Object> rationalizedMap =
                                    invocationOnMock.getArgument(0);
                            given(accessor.getSpanMap()).willReturn(rationalizedMap);
                            return null;
                        })
                .given(accessor)
                .setRationalizedSpanMap(any());

        subject.rationalizeSpan(accessor);
        expansion = spanMapAccessor.getEthTxExpansion(accessor);

        assertExpansionHasNullLinkRefsAnd(OK);
        verify(ethTxData).replaceCallData(unhexedCallData);
    }

    @Test
    void rationalizationUpdatesEthTxCallDataIfLinkedRefsHaveChanged() {
        givenRationalizableAccessor(bodyWithCallData);
        final var curExpansion = new EthTxExpansion(linkedRefs, INVALID_FILE_ID);
        spanMapAccessor.setEthTxExpansion(accessor, curExpansion);

        given(blobs.get(metadataKey)).willReturn(asBlob(undeletedMeta));
        given(blobs.get(dataKey)).willReturn(dataValue);
        given(ethTxData.replaceCallData(unhexedCallData)).willReturn(ethTxData);
        given(syntheticTxnFactory.synthContractOpFromEth(ethTxData))
                .willReturn(Optional.of(synthCallBody));
        willAnswer(
                        invocationOnMock -> {
                            final Map<String, Object> rationalizedMap =
                                    invocationOnMock.getArgument(0);
                            given(accessor.getSpanMap()).willReturn(rationalizedMap);
                            return null;
                        })
                .given(accessor)
                .setRationalizedSpanMap(any());

        subject.rationalizeSpan(accessor);
        expansion = spanMapAccessor.getEthTxExpansion(accessor);

        assertExpansionHasNullLinkRefsAnd(OK);
        verify(ethTxData).replaceCallData(unhexedCallData);
    }

    @Test
    void rationalizationDoesNothingIfLinkedRefsUnchanged() {
        given(accessor.getFunction()).willReturn(EthereumTransaction);
        given(accessor.getSpanMap()).willReturn(spanMap);
        final var curExpansion = new EthTxExpansion(linkedRefs, INVALID_FILE_ID);
        spanMapAccessor.setEthTxExpansion(accessor, curExpansion);
        given(linkedRefs.haveNoChangesAccordingTo(sigImpactHistorian)).willReturn(true);

        subject.rationalizeSpan(accessor);

        assertSame(curExpansion, spanMapAccessor.getEthTxExpansion(accessor));
    }

    private void assertExpansionHasNullLinkRefsAnd(final ResponseCodeEnum status) {
        assertNull(expansion.linkedRefs());
        assertEquals(status, expansion.result());
    }

    private void assertExpansionHasExpectedLinkRefsAnd(final ResponseCodeEnum status) {
        final var refs = expansion.linkedRefs();
        assertEquals(lastHandled, refs.getSourceSignedAt());
        assertArrayEquals(new long[] {666}, refs.linkedNumbers());
        assertEquals(status, expansion.result());
    }

    private void givenExpandableAccessor(final EthereumTransactionBody body) {
        givenUsableAccessor(body);
        given(stateViewFactory.childrenOfLatestSignedState())
                .willReturn(Optional.of(stateChildren));
        given(stateChildren.storage()).willReturn(blobs);
        given(stateChildren.signedAt()).willReturn(lastHandled);
    }

    private void givenRationalizableAccessor(final EthereumTransactionBody body) {
        given(accessor.opEthTxData()).willReturn(ethTxData);
        givenUsableAccessor(body);
        given(workingState.storage()).willReturn(blobs);
    }

    private void givenUsableAccessor(final EthereumTransactionBody body) {
        given(accessor.getFunction()).willReturn(EthereumTransaction);
        given(accessor.getSpanMap()).willReturn(spanMap);
        txn = TransactionBody.newBuilder().setEthereumTransaction(body).build();
        given(accessor.getTxn()).willReturn(txn);
        spanMapAccessor.setEthTxDataMeta(accessor, ethTxData);
    }

    private VirtualBlobValue asBlob(final HFileMeta meta) {
        return new VirtualBlobValue(MetadataMapFactory.toValueBytes(meta));
    }

    private EthTxExpansion expansion;
    private TransactionBody txn;

    private static final Instant lastHandled = Instant.ofEpochSecond(1_234_567, 890);
    private static final FileID callDataId = FileID.newBuilder().setFileNum(666).build();
    private static final EthereumTransactionBody bodyWithCallData =
            EthereumTransactionBody.newBuilder().setCallData(callDataId).build();
    private static final EthereumTransactionBody bodyWithoutCallData =
            EthereumTransactionBody.newBuilder().build();
    private static final byte[] callData = "abcdefabcdefabcdef".getBytes();
    private static final byte[] unhexedCallData = Hex.decode(callData);
    private static final VirtualBlobKey dataKey =
            new VirtualBlobKey(VirtualBlobKey.Type.FILE_DATA, 666);
    private static final VirtualBlobKey metadataKey =
            new VirtualBlobKey(VirtualBlobKey.Type.FILE_METADATA, 666);
    private static final VirtualBlobValue dataValue = new VirtualBlobValue(callData);
    private static final JKey wacl = new JKeyList(List.of());
    private static final HFileMeta deletedMeta = new HFileMeta(true, wacl, 9_999_999);
    private static final HFileMeta undeletedMeta = new HFileMeta(false, wacl, 9_999_999);
    private static final TransactionBody.Builder synthCallBody =
            TransactionBody.newBuilder()
                    .setTransactionID(
                            TransactionID.newBuilder()
                                    .setAccountID(AccountID.newBuilder().setAccountNum(666)))
                    .setContractCall(ContractCallTransactionBody.getDefaultInstance());
    private static final TransactionBody.Builder synthCreateBody =
            TransactionBody.newBuilder()
                    .setTransactionID(
                            TransactionID.newBuilder()
                                    .setAccountID(AccountID.newBuilder().setAccountNum(666)))
                    .setContractCreateInstance(ContractCreateTransactionBody.getDefaultInstance());
}
