package com.hedera.services.utils.accessors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.order.LinkedRefs;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.consensus.SubmitMessageMeta;
import com.hedera.services.usage.crypto.CryptoTransferMeta;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.Map;
import java.util.function.Function;

/**
 * Encapsulates access to several commonly referenced parts of a {@link com.swirlds.common.SwirldTransaction}
 * whose contents is <i>supposed</i> to be a Hedera Services gRPC {@link Transaction}. (The constructor of this
 * class immediately tries to parse the {@code byte[]} contents of the txn, and propagates any protobuf
 * exceptions encountered.)
 */
public class PlatformTxnAccessor implements TxnAccessor {
	private final SwirldTransaction platformTxn;
	private SignedTxnAccessor delegate;

	private RationalizedSigMeta sigMeta = null;

	protected PlatformTxnAccessor(final SignedTxnAccessor delegate,
			SwirldTransaction platformTxn) {
		this.platformTxn = platformTxn;
		this.delegate = delegate;
	}

	public static PlatformTxnAccessor from(final SignedTxnAccessor delegate, final SwirldTransaction platformTxn) {
		return new PlatformTxnAccessor(delegate, platformTxn);
	}

	public static PlatformTxnAccessor from(final SwirldTransaction platformTxn) {
		return new PlatformTxnAccessor(SignedTxnAccessor.from(platformTxn.getContentsDirect()),
				platformTxn);
	}

	public SwirldTransaction getPlatformTxn() {
		return platformTxn;
	}

	public void setSigMeta(RationalizedSigMeta sigMeta) {
		this.sigMeta = sigMeta;
	}

	public RationalizedSigMeta getSigMeta() {
		return sigMeta;
	}

	@Override
	public int sigMapSize() {
		return delegate.sigMapSize();
	}

	@Override
	public int numSigPairs() {
		return delegate.numSigPairs();
	}

	@Override
	public SignatureMap getSigMap() {
		return delegate.getSigMap();
	}

	@Override
	public void setExpandedSigStatus(final ResponseCodeEnum status) {
		delegate.setExpandedSigStatus(status);
	}

	@Override
	public ResponseCodeEnum getExpandedSigStatus() {
		return delegate.getExpandedSigStatus();
	}

	@Override
	public PubKeyToSigBytes getPkToSigsFn() {
		return delegate.getPkToSigsFn();
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
	public byte[] getTxnBytes() {
		return delegate.getTxnBytes();
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
	public ScheduleID getScheduleRef() {
		return delegate.getScheduleRef();
	}

	@Override
	public void setTriggered(final boolean isTriggered) {
		delegate.setTriggered(isTriggered);
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
	public long getGasLimitForContractTx() {
		return delegate.getGasLimitForContractTx();
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
	public void setNumAutoCreations(final int numAutoCreations) {
		delegate.setNumAutoCreations(numAutoCreations);
	}

	@Override
	public int getNumAutoCreations() {
		return delegate.getNumAutoCreations();
	}

	@Override
	public boolean areAutoCreationsCounted() {
		return delegate.areAutoCreationsCounted();
	}

	@Override
	public void countAutoCreationsWith(final AliasManager aliasManager) {
		delegate.countAutoCreationsWith(aliasManager);
	}

	@Override
	public void setLinkedRefs(final LinkedRefs linkedRefs) {
		delegate.setLinkedRefs(linkedRefs);
	}

	@Override
	public LinkedRefs getLinkedRefs() {
		return delegate.getLinkedRefs();
	}

	public Function<byte[], TransactionSignature> getRationalizedPkToCryptoSigFn() {
		if (!sigMeta.couldRationalizeOthers()) {
			throw new IllegalStateException("Public-key-to-crypto-sig mapping is unusable after rationalization " +
					"failed");
		}
		return sigMeta.pkToVerifiedSigFn();
	}

	@Override
	public BaseTransactionMeta baseUsageMeta() {
		return delegate.baseUsageMeta();
	}

	@Override
	public CryptoTransferMeta availXferUsageMeta() {
		return delegate.availXferUsageMeta();
	}

	@Override
	public SubmitMessageMeta availSubmitUsageMeta() {
		return delegate.availSubmitUsageMeta();
	}

	public SignedTxnAccessor getDelegate() {
		return delegate;
	}
}
