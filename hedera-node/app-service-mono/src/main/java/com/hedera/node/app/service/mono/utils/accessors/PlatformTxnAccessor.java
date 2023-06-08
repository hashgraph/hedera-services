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

package com.hedera.node.app.service.mono.utils.accessors;

import com.google.common.base.MoreObjects;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.consensus.SubmitMessageMeta;
import com.hedera.node.app.hapi.fees.usage.crypto.CryptoTransferMeta;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.sigs.sourcing.PojoSigMapPubKeyToSigBytes;
import com.hedera.node.app.service.mono.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.node.app.service.mono.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.node.app.service.mono.utils.PendingCompletion;
import com.hedera.node.app.service.mono.utils.RationalizedSigMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Encapsulates access to commonly referenced parts of a {@link
 * com.swirlds.common.system.transaction.Transaction} whose contents is <i>supposed</i> to be a
 * Hedera Services gRPC {@link Transaction}. (The constructor of this class immediately tries to
 * parse the {@code byte[]} contents of the txn, and propagates any protobuf exceptions
 * encountered.)
 */
public class PlatformTxnAccessor implements SwirldsTxnAccessor {
    private final TxnAccessor delegate;
    private final PubKeyToSigBytes pubKeyToSigBytes;
    private final List<TransactionSignature> cryptoSigs = new ArrayList<>();

    private LinkedRefs linkedRefs;
    private ResponseCodeEnum expandedSigStatus;
    private RationalizedSigMeta sigMeta = null;
    private List<PendingCompletion> pendingCompletions = Collections.emptyList();

    protected PlatformTxnAccessor(final TxnAccessor delegate) {
        this.delegate = delegate;
        pubKeyToSigBytes = new PojoSigMapPubKeyToSigBytes(delegate.getSigMap());
    }

    public static PlatformTxnAccessor from(final Transaction transaction) throws InvalidProtocolBufferException {
        return from(transaction.toByteArray());
    }

    public static PlatformTxnAccessor from(final byte[] contents) throws InvalidProtocolBufferException {
        return new PlatformTxnAccessor(SignedTxnAccessor.from(contents));
    }

    public static PlatformTxnAccessor from(final TxnAccessor accessor) {
        return new PlatformTxnAccessor(accessor);
    }

    @Override
    public synchronized void addAllCryptoSigs(final List<TransactionSignature> signatures) {
        cryptoSigs.addAll(signatures);
    }

    @Override
    public synchronized void clearCryptoSigs() {
        cryptoSigs.clear();
    }

    @Override
    public synchronized List<TransactionSignature> getCryptoSigs() {
        return cryptoSigs;
    }

    @Override
    public void setSigMeta(final RationalizedSigMeta sigMeta) {
        this.sigMeta = sigMeta;
    }

    @Override
    public RationalizedSigMeta getSigMeta() {
        return sigMeta;
    }

    @Override
    public boolean hasConsequentialUnknownFields() {
        return delegate.hasConsequentialUnknownFields();
    }

    @Override
    public String toLoggableString() {
        return MoreObjects.toStringHelper(this)
                .add("delegate", delegate.toLoggableString())
                .add("linkedRefs", linkedRefs)
                .add("expandedSigStatus", expandedSigStatus)
                .add("pubKeyToSigBytes", pubKeyToSigBytes.toString())
                .add("sigMeta", sigMeta)
                .toString();
    }

    @Override
    public void setExpandedSigStatus(final ResponseCodeEnum status) {
        this.expandedSigStatus = status;
    }

    @Override
    public ResponseCodeEnum getExpandedSigStatus() {
        return expandedSigStatus;
    }

    @Override
    public PubKeyToSigBytes getPkToSigsFn() {
        return pubKeyToSigBytes;
    }

    @Override
    public void setLinkedRefs(final LinkedRefs linkedRefs) {
        this.linkedRefs = linkedRefs;
    }

    @Override
    public LinkedRefs getLinkedRefs() {
        return linkedRefs;
    }

    @Override
    public void setPendingCompletions(final List<PendingCompletion> pendingCompletions) {
        this.pendingCompletions = pendingCompletions;
    }

    @Override
    public List<PendingCompletion> getPendingCompletions() {
        return this.pendingCompletions;
    }

    @Override
    public Function<byte[], TransactionSignature> getRationalizedPkToCryptoSigFn() {
        final var meta = getSigMeta();
        if (!meta.couldRationalizeOthers()) {
            throw new IllegalStateException("Public-key-to-sig mapping is unusable after rationalization failed");
        }
        return meta.pkToVerifiedSigFn();
    }

    @Override
    public TxnAccessor getDelegate() {
        return delegate;
    }

    /* ---Delegates to SignedTxnAccessor --- */

    @Override
    public byte[] getTxnBytes() {
        return delegate.getTxnBytes();
    }

    @Override
    public long getOfferedFee() {
        return delegate.getOfferedFee();
    }

    @Override
    public AccountID getPayer() {
        return delegate.getPayer();
    }

    @Override
    public TransactionID getTxnId() {
        return delegate.getTxnId();
    }

    @Override
    public HederaFunctionality getFunction() {
        return delegate.getFunction();
    }

    @Override
    public SubType getSubType() {
        return delegate.getSubType();
    }

    @Override
    public byte[] getMemoUtf8Bytes() {
        return delegate.getMemoUtf8Bytes();
    }

    @Override
    public String getMemo() {
        return delegate.getMemo();
    }

    @Override
    public boolean memoHasZeroByte() {
        return delegate.memoHasZeroByte();
    }

    @Override
    public byte[] getHash() {
        return delegate.getHash();
    }

    @Override
    public TransactionBody getTxn() {
        return delegate.getTxn();
    }

    @Override
    public boolean canTriggerTxn() {
        return delegate.canTriggerTxn();
    }

    @Override
    public boolean isTriggeredTxn() {
        return delegate.isTriggeredTxn();
    }

    @Override
    public boolean throttleExempt() {
        return delegate.throttleExempt();
    }

    @Override
    public void markThrottleExempt() {
        delegate.markThrottleExempt();
    }

    @Override
    public boolean congestionExempt() {
        return delegate.congestionExempt();
    }

    @Override
    public void markCongestionExempt() {
        delegate.markCongestionExempt();
    }

    @Override
    public ScheduleID getScheduleRef() {
        return delegate.getScheduleRef();
    }

    @Override
    public void setScheduleRef(final ScheduleID parent) {
        delegate.setScheduleRef(parent);
    }

    @Override
    public void setPayer(final AccountID payer) {
        delegate.setPayer(payer);
    }

    @Override
    public byte[] getSignedTxnWrapperBytes() {
        return delegate.getSignedTxnWrapperBytes();
    }

    @Override
    public Transaction getSignedTxnWrapper() {
        return delegate.getSignedTxnWrapper();
    }

    @Override
    public long getGasLimitForContractTx() {
        return delegate.getGasLimitForContractTx();
    }

    @Override
    public void setRationalizedSpanMap(final Map<String, Object> newSpanMap) {
        delegate.setRationalizedSpanMap(newSpanMap);
    }

    @Override
    public Map<String, Object> getSpanMap() {
        return delegate.getSpanMap();
    }

    @Override
    public ExpandHandleSpanMapAccessor getSpanMapAccessor() {
        return delegate.getSpanMapAccessor();
    }

    @Override
    public void setStateView(final StateView view) {
        delegate.setStateView(view);
    }

    @Override
    public void setNumImplicitCreations(final int numImplicitCreations) {
        delegate.setNumImplicitCreations(numImplicitCreations);
    }

    @Override
    public int getNumImplicitCreations() {
        return delegate.getNumImplicitCreations();
    }

    @Override
    public boolean areImplicitCreationsCounted() {
        return delegate.areImplicitCreationsCounted();
    }

    @Override
    public void countImplicitCreationsWith(final AliasManager aliasManager) {
        delegate.countImplicitCreationsWith(aliasManager);
    }

    @Override
    public EthTxData opEthTxData() {
        return delegate.opEthTxData();
    }

    @Override
    public SignatureMap getSigMap() {
        return delegate.getSigMap();
    }

    @Override
    public BaseTransactionMeta baseUsageMeta() {
        return delegate.baseUsageMeta();
    }

    @Override
    public SigUsage usageGiven(final int numPayerKeys) {
        return delegate.usageGiven(numPayerKeys);
    }

    @Override
    public CryptoTransferMeta availXferUsageMeta() {
        return delegate.availXferUsageMeta();
    }

    @Override
    public SubmitMessageMeta availSubmitUsageMeta() {
        return delegate.availSubmitUsageMeta();
    }

    @Override
    public StateView getStateView() {
        return delegate.getStateView();
    }

    @Override
    public boolean mintsWithMetadata() {
        return delegate.mintsWithMetadata();
    }
}
