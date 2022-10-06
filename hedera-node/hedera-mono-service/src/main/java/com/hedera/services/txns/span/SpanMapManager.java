/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.state.merkle.internals.BitPackUtils.codeFromNum;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.StateChildren;
import com.hedera.services.context.primitives.SignedStateViewFactory;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.files.MetadataMapFactory;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.order.LinkedRefs;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.txns.contract.ContractCallTransitionLogic;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.virtualmap.VirtualMap;
import java.util.*;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.Hex;

/**
 * Responsible for managing the properties in a {@link TxnAccessor#getSpanMap()}. This management
 * happens in two steps:
 *
 * <ol>
 *   <li>In {@link SpanMapManager#expandSpan(TxnAccessor)}, the span map is "expanded" to include
 *       the results of any work that can likely be reused in {@code handleTransaction}.
 *   <li>In {@link SpanMapManager#rationalizeSpan(TxnAccessor)}, the span map "rationalized" to be
 *       sure that any pre-computed work can still be reused safely.
 * </ol>
 *
 * The only entry currently in the span map is the {@link
 * com.hedera.services.grpc.marshalling.ImpliedTransfers} produced by the {@link
 * ImpliedTransfersMarshal}; this improves performance for CrypoTransfers specifically.
 *
 * <p>Other operations will certainly be able to benefit from the same infrastructure over time.
 */
@Singleton
public class SpanMapManager {
    private final AliasManager aliasManager;
    private final SigImpactHistorian sigImpactHistorian;
    private final MutableStateChildren workingState;
    private final CustomFeeSchedules customFeeSchedules;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final GlobalDynamicProperties dynamicProperties;
    private final SignedStateViewFactory stateViewFactory;
    private final ImpliedTransfersMarshal impliedTransfersMarshal;
    private final ExpandHandleSpanMapAccessor spanMapAccessor;
    private final ContractCallTransitionLogic contractCallTransitionLogic;
    private final Function<EthTxData, EthTxSigs> sigsFunction;

    @Inject
    public SpanMapManager(
            final Function<EthTxData, EthTxSigs> sigsFunction,
            final ContractCallTransitionLogic contractCallTransitionLogic,
            final ExpandHandleSpanMapAccessor spanMapAccessor,
            final ImpliedTransfersMarshal impliedTransfersMarshal,
            final GlobalDynamicProperties dynamicProperties,
            final SignedStateViewFactory stateViewFactory,
            final SyntheticTxnFactory syntheticTxnFactory,
            final CustomFeeSchedules customFeeSchedules,
            final SigImpactHistorian sigImpactHistorian,
            final MutableStateChildren workingState,
            final AliasManager aliasManager) {
        this.contractCallTransitionLogic = contractCallTransitionLogic;
        this.impliedTransfersMarshal = impliedTransfersMarshal;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.sigImpactHistorian = sigImpactHistorian;
        this.dynamicProperties = dynamicProperties;
        this.customFeeSchedules = customFeeSchedules;
        this.stateViewFactory = stateViewFactory;
        this.spanMapAccessor = spanMapAccessor;
        this.sigsFunction = sigsFunction;
        this.workingState = workingState;
        this.aliasManager = aliasManager;
    }

    public void expandSpan(TxnAccessor accessor) {
        final var function = accessor.getFunction();
        if (function == CryptoTransfer) {
            expandImpliedTransfers(accessor);
        }
    }

    /**
     * Given an accessor for an {@link com.hederahashgraph.api.proto.java.EthereumTransaction}, uses
     * the latest signed state to attempt the following pre-computation:
     *
     * <ol>
     *   <li>Fetch the call data, if needed, from the signed state's blobs {@code VirtualMap}; and,
     *   <li>Recover the signing key as a {@link com.hedera.services.ethereum.EthTxSigs}; and,
     *   <li>Create the synthetic transaction implied by the above two results; and,
     *   <li>If this is a synthetic contract call, run {@link
     *       ContractCallTransitionLogic#preFetch(TxnAccessor)}.
     * </ol>
     *
     * For the first three, adds each computed result to the accessor's span map for re-use in
     * {@code handleTransaction}.
     *
     * <p>As an additional side effect, this method adds an instance of {@link EthTxExpansion} to
     * the accessor's span map. The {@link com.hedera.services.sigs.order.LinkedRefs} instance in
     * the expansion can be used in {@code handleTransaction} to validate that no input to the
     * pre-computed results has changed. Furthermore, if any of the pre-computation steps failed
     * (e.g., the call data file did not exist), the expansion will include that failure status, and
     * {@code handleTransaction} can fail immediately if nothing has changed.
     *
     * @param accessor an EthereumTransaction accessor
     */
    public void expandEthereumSpan(final TxnAccessor accessor) {
        final var stateChildren = stateViewFactory.childrenOfLatestSignedState();
        if (stateChildren.isEmpty()) {
            // The Ethereum context will have to be computed synchronously in handleTransaction
            return;
        }
        final var signedStateChildren = stateChildren.get();
        final var linkedRefs = new LinkedRefs(signedStateChildren.signedAt());
        try {
            expandEthContext(accessor, signedStateChildren, accessor.getSpanMap(), linkedRefs);
        } catch (UnsupportedOperationException ignore) {
            // Thrown if the span map is immutable; means pre-fetch is somehow backlogged and the
            // handleTransaction thread already rationalized and set the authoritative span map
        }
    }

    /**
     * Called at the beginning of {@code handleTransaction} to check if any pre-computed work stored
     * inside the given {@link TxnAccessor} needs to be re-computed because some linked entity has
     * changed.
     *
     * @param accessor the accessor representing the transaction about to be handled
     */
    public void rationalizeSpan(final TxnAccessor accessor) {
        final var function = accessor.getFunction();
        if (function == CryptoTransfer) {
            rationalizeImpliedTransfers(accessor);
        } else if (function == EthereumTransaction) {
            rationalizeEthereumSpan(accessor);
        }
    }

    private void rationalizeEthereumSpan(final TxnAccessor accessor) {
        final var expansion = spanMapAccessor.getEthTxExpansion(accessor);
        if (expansion == null || areChanged(Objects.requireNonNull(expansion.linkedRefs()))) {
            final Map<String, Object> spanMap = new HashMap<>();
            spanMapAccessor.setEthTxDataMeta(spanMap, accessor.opEthTxData());
            expandEthContext(accessor, workingState, spanMap, null);
            accessor.setRationalizedSpanMap(spanMap);
        }
    }

    private boolean areChanged(final LinkedRefs linkedRefs) {
        return !linkedRefs.haveNoChangesAccordingTo(sigImpactHistorian);
    }

    private void expandEthContext(
            final TxnAccessor accessor,
            final StateChildren stateChildren,
            final Map<String, Object> spanMap,
            @Nullable final LinkedRefs linkedRefs) {
        assertIsEthTxn(accessor);
        var ethTxData = spanMapAccessor.getEthTxDataMeta(spanMap);
        final var op = accessor.getTxn().getEthereumTransaction();

        // This reference summarizes the results of the three pre-computation steps; if a step
        // fails, it sets  the reference to a non-null EthTxExpansion with appropriate error code,
        // and any remaining steps will be skipped
        EthTxExpansion expansion = null;

        // (1) Try retrieve the call data (if it's in a file) and set in spanMap
        if (op.hasCallData() && !ethTxData.hasCallData()) {
            final var result =
                    computeCallData(
                            ethTxData,
                            op.getCallData(),
                            linkedRefs,
                            spanMap,
                            stateChildren.storage());
            if (result.getKey() != OK) {
                expansion = new EthTxExpansion(linkedRefs, result.getKey());
            } else {
                ethTxData = result.getValue();
            }
        }
        // (2) Try to extract the signature and set in spanMap
        if (expansion == null) {
            expansion = expandEthTxSigs(spanMap, ethTxData, linkedRefs);
        }
        // (3) Try to synthesize the specialized TransactionBody and set in spanMap
        if (expansion == null) {
            expansion = expandSynthTxn(spanMap, ethTxData, linkedRefs);
        }

        // If no steps failed, summarize as OK
        if (expansion == null) {
            expansion = new EthTxExpansion(linkedRefs, OK);
        }
        spanMapAccessor.setEthTxExpansion(spanMap, expansion);
    }

    @Nullable
    private EthTxExpansion expandSynthTxn(
            final Map<String, Object> spanMap,
            final EthTxData ethTxData,
            @Nullable final LinkedRefs linkedRefs) {
        final var opBuilder = syntheticTxnFactory.synthContractOpFromEth(ethTxData);
        if (opBuilder.isEmpty()) {
            return new EthTxExpansion(linkedRefs, INVALID_ETHEREUM_TRANSACTION);
        }
        final var txn = opBuilder.get().build();
        if (txn.hasContractCall()) {
            contractCallTransitionLogic.preFetchOperation(txn.getContractCall());
        }
        spanMapAccessor.setEthTxBodyMeta(spanMap, txn);
        return null;
    }

    @Nullable
    private EthTxExpansion expandEthTxSigs(
            final Map<String, Object> spanMap,
            final EthTxData ethTxData,
            @Nullable final LinkedRefs linkedRefs) {
        try {
            final var ethTxSigs = sigsFunction.apply(ethTxData);
            spanMapAccessor.setEthTxSigsMeta(spanMap, ethTxSigs);
            return null;
        } catch (IllegalArgumentException ignore) {
            return new EthTxExpansion(linkedRefs, INVALID_ETHEREUM_TRANSACTION);
        }
    }

    private void assertIsEthTxn(final TxnAccessor accessor) {
        final var function = accessor.getFunction();
        if (function != EthereumTransaction) {
            throw new IllegalArgumentException(
                    "Cannot expand Ethereum span for a " + function + " accessor");
        }
    }

    private Pair<ResponseCodeEnum, EthTxData> computeCallData(
            EthTxData ethTxData,
            final FileID callDataId,
            @Nullable final LinkedRefs linkedRefs,
            final Map<String, Object> spanMap,
            final VirtualMap<VirtualBlobKey, VirtualBlobValue> curBlobs) {
        if (linkedRefs != null) {
            linkedRefs.link(callDataId.getFileNum());
        }
        final var fileMeta = curBlobs.get(metadataKeyFor(callDataId));
        if (fileMeta == null) {
            return Pair.of(INVALID_FILE_ID, ethTxData);
        } else {
            final var callDataAttr =
                    Objects.requireNonNull(MetadataMapFactory.toAttr(fileMeta.getData()));
            if (callDataAttr.isDeleted()) {
                return Pair.of(FILE_DELETED, ethTxData);
            } else {
                final var hexedCallData =
                        Objects.requireNonNull(curBlobs.get(dataKeyFor(callDataId))).getData();
                final byte[] callData;
                try {
                    callData = Hex.decode(hexedCallData);
                } catch (final DecoderException ignore) {
                    return Pair.of(INVALID_FILE_ID, ethTxData);
                }
                if (callData.length == 0) {
                    return Pair.of(CONTRACT_FILE_EMPTY, ethTxData);
                } else {
                    ethTxData = ethTxData.replaceCallData(callData);
                    spanMapAccessor.setEthTxDataMeta(spanMap, ethTxData);
                }
            }
        }
        return Pair.of(OK, ethTxData);
    }

    private VirtualBlobKey dataKeyFor(final FileID fileId) {
        return new VirtualBlobKey(VirtualBlobKey.Type.FILE_DATA, codeFromNum(fileId.getFileNum()));
    }

    private VirtualBlobKey metadataKeyFor(final FileID fileId) {
        return new VirtualBlobKey(
                VirtualBlobKey.Type.FILE_METADATA, codeFromNum(fileId.getFileNum()));
    }

    private void rationalizeImpliedTransfers(TxnAccessor accessor) {
        final var impliedTransfers = spanMapAccessor.getImpliedTransfers(accessor);
        if (!impliedTransfers
                .getMeta()
                .wasDerivedFrom(dynamicProperties, customFeeSchedules, aliasManager)) {
            expandImpliedTransfers(accessor);
        }
    }

    private void expandImpliedTransfers(TxnAccessor accessor) {
        final var op = accessor.getTxn().getCryptoTransfer();
        final var impliedTransfers =
                impliedTransfersMarshal.unmarshalFromGrpc(op, accessor.getPayer());
        reCalculateXferMeta(accessor, impliedTransfers);
        spanMapAccessor.setImpliedTransfers(accessor, impliedTransfers);
        accessor.setNumAutoCreations(impliedTransfers.getMeta().getNumAutoCreations());
    }

    public static void reCalculateXferMeta(
            TxnAccessor accessor, ImpliedTransfers impliedTransfers) {
        final var xferMeta = accessor.availXferUsageMeta();

        var customFeeTokenTransfers = 0;
        var customFeeHbarTransfers = 0;
        final Set<EntityId> involvedTokens = new HashSet<>();
        for (var assessedFee : impliedTransfers.getAssessedCustomFees()) {
            if (assessedFee.isForHbar()) {
                customFeeHbarTransfers++;
            } else {
                customFeeTokenTransfers++;
                involvedTokens.add(assessedFee.token());
            }
        }
        xferMeta.setCustomFeeHbarTransfers(customFeeHbarTransfers);
        xferMeta.setCustomFeeTokensInvolved(involvedTokens.size());
        xferMeta.setCustomFeeTokenTransfers(customFeeTokenTransfers);
    }
}
