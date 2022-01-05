package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hedera.services.grpc.marshalling.AliasResolver;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.order.LinkedRefs;
import com.hedera.services.sigs.sourcing.PojoSigMapPubKeyToSigBytes;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.consensus.SubmitMessageMeta;
import com.hedera.services.usage.crypto.CryptoCreateMeta;
import com.hedera.services.usage.crypto.CryptoTransferMeta;
import com.hedera.services.usage.crypto.CryptoUpdateMeta;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Arrays;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hedera.services.utils.MiscUtils.functionOf;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;

/**
 * Encapsulates access to several commonly referenced parts of a gRPC {@link Transaction}.
 */
public class SignedTxnAccessor implements TxnAccessor {
	private static final Logger log = LogManager.getLogger(SignedTxnAccessor.class);

	private static final int UNKNOWN_NUM_AUTO_CREATIONS = -1;
	private static final String ACCESSOR_LITERAL = " accessor";

	private static final TokenOpsUsage TOKEN_OPS_USAGE = new TokenOpsUsage();
	private static final ExpandHandleSpanMapAccessor SPAN_MAP_ACCESSOR = new ExpandHandleSpanMapAccessor();

	private final Map<String, Object> spanMap = new HashMap<>();

	private int sigMapSize;
	private int numSigPairs;
	private int numAutoCreations = UNKNOWN_NUM_AUTO_CREATIONS;
	private byte[] hash;
	private byte[] txnBytes;
	private byte[] utf8MemoBytes;
	private byte[] signedTxnWrapperBytes;
	private String memo;
	private boolean memoHasZeroByte;
	private LinkedRefs linkedRefs;
	private Transaction signedTxnWrapper;
	private SignatureMap sigMap;
	private TransactionID txnId;
	private TransactionBody txn;
	private ResponseCodeEnum expandedSigStatus;
	private PubKeyToSigBytes pubKeyToSigBytes;
	private SubmitMessageMeta submitMessageMeta;
	private CryptoTransferMeta xferUsageMeta;
	private BaseTransactionMeta txnUsageMeta;
	private HederaFunctionality function;

	static Function<TransactionBody, HederaFunctionality> functionExtractor = txn -> {
		try {
			return functionOf(txn);
		} catch (UnknownHederaFunctionality ignore) {
			return NONE;
		}
	};

	public static SignedTxnAccessor uncheckedFrom(Transaction validSignedTxn) {
		try {
			return new SignedTxnAccessor(validSignedTxn);
		} catch (Exception illegal) {
			log.warn("Unexpected use of factory with invalid gRPC transaction", illegal);
			throw new IllegalArgumentException("Argument 'validSignedTxn' must be a valid signed txn");
		}
	}

	public SignedTxnAccessor(byte[] signedTxnWrapperBytes) throws InvalidProtocolBufferException {
		this.signedTxnWrapperBytes = signedTxnWrapperBytes;
		signedTxnWrapper = Transaction.parseFrom(signedTxnWrapperBytes);

		final var signedTxnBytes = signedTxnWrapper.getSignedTransactionBytes();
		if (signedTxnBytes.isEmpty()) {
			txnBytes = signedTxnWrapper.getBodyBytes().toByteArray();
			sigMap = signedTxnWrapper.getSigMap();
			hash = noThrowSha384HashOf(signedTxnWrapperBytes);
		} else {
			final var signedTxn = SignedTransaction.parseFrom(signedTxnBytes);
			txnBytes = signedTxn.getBodyBytes().toByteArray();
			sigMap = signedTxn.getSigMap();
			hash = noThrowSha384HashOf(signedTxnBytes.toByteArray());
		}
		pubKeyToSigBytes = new PojoSigMapPubKeyToSigBytes(sigMap);

		txn = TransactionBody.parseFrom(txnBytes);
		memo = txn.getMemo();
		txnId = txn.getTransactionID();
		sigMapSize = sigMap.getSerializedSize();
		numSigPairs = sigMap.getSigPairCount();
		utf8MemoBytes = StringUtils.getBytesUtf8(memo);
		memoHasZeroByte = Arrays.contains(utf8MemoBytes, (byte) 0);

		getFunction();
		setBaseUsageMeta();
		setOpUsageMeta();
	}

	public SignedTxnAccessor(Transaction signedTxnWrapper) throws InvalidProtocolBufferException {
		this(signedTxnWrapper.toByteArray());
	}

	@Override
	public void setExpandedSigStatus(final ResponseCodeEnum expandedSigStatus) {
		this.expandedSigStatus = expandedSigStatus;
	}

	@Override
	public ResponseCodeEnum getExpandedSigStatus() {
		return expandedSigStatus;
	}

	@Override
	public LinkedRefs getLinkedRefs() {
		return linkedRefs;
	}

	@Override
	public void setLinkedRefs(final LinkedRefs linkedRefs) {
		this.linkedRefs = linkedRefs;
	}

	@Override
	public void countAutoCreationsWith(final AliasManager aliasManager) {
		final var resolver = new AliasResolver();
		resolver.resolve(txn.getCryptoTransfer(), aliasManager);
		numAutoCreations = resolver.perceivedAutoCreations();
	}

	@Override
	public void setNumAutoCreations(final int numAutoCreations) {
		this.numAutoCreations = numAutoCreations;
	}

	@Override
	public int getNumAutoCreations() {
		return numAutoCreations;
	}

	@Override
	public boolean areAutoCreationsCounted() {
		return numAutoCreations != UNKNOWN_NUM_AUTO_CREATIONS;
	}

	@Override
	public SignatureMap getSigMap() {
		return sigMap;
	}

	@Override
	public HederaFunctionality getFunction() {
		if (function == null) {
			function = functionExtractor.apply(getTxn());
		}
		return function;
	}

	@Override
	public SubType getSubType() {
		if (function == CryptoTransfer) {
			return xferUsageMeta.getSubType();
		} else if (function == TokenCreate) {
			return SPAN_MAP_ACCESSOR.getTokenCreateMeta(this).getSubType();
		} else if (function == TokenMint) {
			final var op = getTxn().getTokenMint();
			return op.getMetadataCount() > 0 ? TOKEN_NON_FUNGIBLE_UNIQUE : TOKEN_FUNGIBLE_COMMON;
		} else if (function == TokenBurn) {
			return SPAN_MAP_ACCESSOR.getTokenBurnMeta(this).getSubType();
		} else if (function == TokenAccountWipe) {
			return SPAN_MAP_ACCESSOR.getTokenWipeMeta(this).getSubType();
		}
		return SubType.DEFAULT;
	}

	@Override
	public long getOfferedFee() {
		return txn.getTransactionFee();
	}

	@Override
	public byte[] getTxnBytes() {
		return txnBytes;
	}

	@Override
	public Transaction getSignedTxnWrapper() {
		return signedTxnWrapper;
	}

	@Override
	public TransactionBody getTxn() {
		return txn;
	}

	@Override
	public TransactionID getTxnId() {
		return txnId;
	}

	@Override
	public AccountID getPayer() {
		return getTxnId().getAccountID();
	}

	@Override
	public byte[] getSignedTxnWrapperBytes() {
		return signedTxnWrapperBytes;
	}

	@Override
	public byte[] getMemoUtf8Bytes() {
		return utf8MemoBytes;
	}

	@Override
	public int numSigPairs() {
		return numSigPairs;
	}

	@Override
	public int sigMapSize() {
		return sigMapSize;
	}

	@Override
	public String getMemo() {
		return memo;
	}

	@Override
	public byte[] getHash() {
		return hash;
	}

	@Override
	public boolean canTriggerTxn() {
		return getTxn().hasScheduleCreate() || getTxn().hasScheduleSign();
	}

	@Override
	public boolean memoHasZeroByte() {
		return memoHasZeroByte;
	}

	@Override
	public boolean isTriggeredTxn() {
		return false;
	}

	@Override
	public ScheduleID getScheduleRef() {
		throw new UnsupportedOperationException("Only the TriggeredTxnAccessor implementation can refer to a schedule");
	}

	@Override
	public BaseTransactionMeta baseUsageMeta() {
		return txnUsageMeta;
	}

	@Override
	public CryptoTransferMeta availXferUsageMeta() {
		if (function != CryptoTransfer) {
			throw new IllegalStateException("Cannot get CryptoTransfer metadata for a " + function + ACCESSOR_LITERAL);
		}
		return xferUsageMeta;
	}

	@Override
	public SubmitMessageMeta availSubmitUsageMeta() {
		if (function != ConsensusSubmitMessage) {
			throw new IllegalStateException(
					"Cannot get ConsensusSubmitMessage metadata for a " + function + ACCESSOR_LITERAL);
		}
		return submitMessageMeta;
	}

	@Override
	public PubKeyToSigBytes getPkToSigsFn() {
		return pubKeyToSigBytes;
	}

	@Override
	public Map<String, Object> getSpanMap() {
		return spanMap;
	}

	@Override
	public ExpandHandleSpanMapAccessor getSpanMapAccessor() {
		return SPAN_MAP_ACCESSOR;
	}

	private void setOpUsageMeta() {
		if (function == CryptoTransfer) {
			setXferUsageMeta();
		} else if (function == ConsensusSubmitMessage) {
			setSubmitUsageMeta();
		} else if (function == TokenFeeScheduleUpdate) {
			setFeeScheduleUpdateMeta();
		} else if (function == TokenCreate) {
			setTokenCreateUsageMeta();
		} else if (function == TokenBurn) {
			setTokenBurnUsageMeta();
		} else if (function == TokenAccountWipe) {
			setTokenWipeUsageMeta();
		} else if (function == TokenFreezeAccount) {
			setTokenFreezeUsageMeta();
		} else if (function == TokenUnfreezeAccount) {
			setTokenUnfreezeUsageMeta();
		} else if (function == TokenPause) {
			setTokenPauseUsageMeta();
		} else if (function == TokenUnpause) {
			setTokenUnpauseUsageMeta();
		} else if (function == CryptoCreate) {
			setCryptoCreateUsageMeta();
		} else if (function == CryptoUpdate) {
			setCryptoUpdateUsageMeta();
		}
	}

	private void setXferUsageMeta() {
		var totalTokensInvolved = 0;
		var totalTokenTransfers = 0;
		var numNftOwnershipChanges = 0;
		final var op = txn.getCryptoTransfer();
		for (var tokenTransfers : op.getTokenTransfersList()) {
			totalTokensInvolved++;
			totalTokenTransfers += tokenTransfers.getTransfersCount();
			numNftOwnershipChanges += tokenTransfers.getNftTransfersCount();
		}
		xferUsageMeta = new CryptoTransferMeta(1, totalTokensInvolved, totalTokenTransfers, numNftOwnershipChanges);
	}

	private void setSubmitUsageMeta() {
		submitMessageMeta = new SubmitMessageMeta(txn.getConsensusSubmitMessage().getMessage().size());
	}

	private void setFeeScheduleUpdateMeta() {
		final var effConsTime = getTxnId().getTransactionValidStart().getSeconds();
		final var op = getTxn().getTokenFeeScheduleUpdate();
		final var reprBytes = TOKEN_OPS_USAGE.bytesNeededToRepr(op.getCustomFeesList());

		final var meta = new FeeScheduleUpdateMeta(effConsTime, reprBytes);
		SPAN_MAP_ACCESSOR.setFeeScheduleUpdateMeta(this, meta);
	}

	private void setTokenCreateUsageMeta() {
		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);
		SPAN_MAP_ACCESSOR.setTokenCreateMeta(this, tokenCreateMeta);
	}

	private void setTokenBurnUsageMeta() {
		final var tokenBurnMeta = TOKEN_OPS_USAGE_UTILS.tokenBurnUsageFrom(txn);
		SPAN_MAP_ACCESSOR.setTokenBurnMeta(this, tokenBurnMeta);
	}

	private void setTokenWipeUsageMeta() {
		final var tokenWipeMeta = TOKEN_OPS_USAGE_UTILS.tokenWipeUsageFrom(txn);
		SPAN_MAP_ACCESSOR.setTokenWipeMeta(this, tokenWipeMeta);
	}

	private void setTokenFreezeUsageMeta() {
		final var tokenFreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenFreezeUsageFrom();
		SPAN_MAP_ACCESSOR.setTokenFreezeMeta(this, tokenFreezeMeta);
	}

	private void setTokenUnfreezeUsageMeta() {
		final var tokenUnfreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenUnfreezeUsageFrom();
		SPAN_MAP_ACCESSOR.setTokenUnfreezeMeta(this, tokenUnfreezeMeta);
	}

	private void setTokenPauseUsageMeta() {
		final var tokenPauseMeta = TOKEN_OPS_USAGE_UTILS.tokenPauseUsageFrom();
		SPAN_MAP_ACCESSOR.setTokenPauseMeta(this, tokenPauseMeta);
	}

	private void setTokenUnpauseUsageMeta() {
		final var tokenUnpauseMeta = TOKEN_OPS_USAGE_UTILS.tokenUnpauseUsageFrom();
		SPAN_MAP_ACCESSOR.setTokenUnpauseMeta(this, tokenUnpauseMeta);
	}

	private void setCryptoCreateUsageMeta() {
		final var cryptoCreateMeta = new CryptoCreateMeta(txn.getCryptoCreateAccount());
		SPAN_MAP_ACCESSOR.setCryptoCreateMeta(this, cryptoCreateMeta);
	}

	private void setCryptoUpdateUsageMeta() {
		final var cryptoUpdateMeta = new CryptoUpdateMeta(txn.getCryptoUpdateAccount(),
				txn.getTransactionID().getTransactionValidStart().getSeconds());
		SPAN_MAP_ACCESSOR.setCryptoUpdate(this, cryptoUpdateMeta);
	}

	private void setBaseUsageMeta() {
		if (function == CryptoTransfer) {
			txnUsageMeta = new BaseTransactionMeta(
					utf8MemoBytes.length,
					txn.getCryptoTransfer().getTransfers().getAccountAmountsCount());
		} else {
			txnUsageMeta = new BaseTransactionMeta(utf8MemoBytes.length, 0);
		}
	}
}
