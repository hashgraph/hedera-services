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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.codec.binary.StringUtils;

import java.util.function.Function;

import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.utils.MiscUtils.functionOf;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;

/**
 * Encapsulates access to several commonly referenced parts of a gRPC {@link Transaction}.
 *
 * @author Michael Tinker
 */
public class SignedTxnAccessor implements TxnAccessor {
	private int sigMapSize;
	private int numSigPairs;
	private byte[] hash;
	private byte[] txnBytes;
	private byte[] utf8MemoBytes;
	private byte[] signedTxnWrapperBytes;
	private boolean memoHasZeroByte;
	private Transaction signedTxnWrapper;
	private SignatureMap sigMap;
	private TransactionID txnId;
	private TransactionBody txn;
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
		} catch (Exception impossible) {
		}
		return null;
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

		txn = TransactionBody.parseFrom(txnBytes);
		txnId = txn.getTransactionID();
		sigMapSize = sigMap.getSerializedSize();
		numSigPairs = sigMap.getSigPairCount();
		utf8MemoBytes = StringUtils.getBytesUtf8(txn.getMemo());
	}

	public SignedTxnAccessor(Transaction signedTxnWrapper) throws InvalidProtocolBufferException {
		this(signedTxnWrapper.toByteArray());
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
	public long getOfferedFee() {
		return txn.getTransactionFee();
	}

	@Override
	public Transaction getSignedTxn4Log() {
		return signedTxnWrapper;
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
	public byte[] getHash() {
		return hash;
	}

	@Override
	public boolean canTriggerTxn() {
		return getTxn().hasScheduleCreate() || getTxn().hasScheduleSign();
	}

	@Override
	public boolean memoHasZeroByte() {
		throw new AssertionError("Not implemented!");
	}

	public boolean isTriggeredTxn() {
		return false;
	}

	public ScheduleID getScheduleRef() {
		throw new UnsupportedOperationException("Only the TriggeredTxnAccessor implementation can refer to a schedule");
	}
}
