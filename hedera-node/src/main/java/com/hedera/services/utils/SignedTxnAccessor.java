package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;

import java.util.List;
import java.util.function.Function;

import static com.hedera.services.utils.MiscUtils.functionOf;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;

/**
 * Encapsulates access to several commonly referenced parts of a gRPC {@link Transaction}.
 *
 * @author Michael Tinker
 */
public class SignedTxnAccessor {
	private byte[] txnBytes;
	private byte[] signedTxnBytes;
	private Transaction signedTxn4Log;
	private Transaction signedTxn;
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
		} catch (Exception impossible) {}
		return null;
	}

	public SignedTxnAccessor(byte[] signedTxnBytes) throws InvalidProtocolBufferException {
		this.signedTxnBytes = signedTxnBytes;
		signedTxn = Transaction.parseFrom(signedTxnBytes);
		if (signedTxn.hasBody()) {
			txn = signedTxn.getBody();
			txnBytes = txn.toByteArray();
		} else {
			txnBytes = signedTxn.getBodyBytes().toByteArray();
			txn = TransactionBody.parseFrom(txnBytes);
		}
		txnId = txn.getTransactionID();
	}

	public SignedTxnAccessor(Transaction signedTxn) throws InvalidProtocolBufferException {
		this(signedTxn.toByteArray());
	}

	public HederaFunctionality getFunction() {
		if (function == null) {
			function = functionExtractor.apply(getTxn());
		}
		return function;
	}

	public Transaction getSignedTxn4Log() {
		if (signedTxn4Log == null) {
			try {
				signedTxn4Log = signedTxn.hasBody() ? signedTxn : signedTxn.toBuilder()
						.setBody(TransactionBody.parseFrom(signedTxn.getBodyBytes()))
						.clearBodyBytes()
						.build();
			} catch (InvalidProtocolBufferException ignore) { }
		}
		return signedTxn4Log;
	}

	public byte[] getTxnBytes() {
		return txnBytes;
	}

	public Transaction getSignedTxn() {
		return signedTxn;
	}

	public List<Signature> getSigsList() {
		return signedTxn.getSigs().getSigsList();
	}

	public TransactionBody getTxn() {
		return txn;
	}

	public TransactionID getTxnId() {
		return txnId;
	}

	public AccountID getPayer() {
		return getTxnId().getAccountID();
	}

	public byte[] getSignedTxnBytes() {
		return signedTxnBytes;
	}
}
