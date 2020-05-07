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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;

import java.util.List;

/**
 * Encapsulates access to several commonly referenced parts of a gRPC {@link Transaction}.
 *
 * @author Michael Tinker
 */
public class SignedTxnAccessor {
	private Transaction signedTxn4Log;
	private byte[] txnBytes;
	private Transaction signedTxn;
	private TransactionID txnId;
	private TransactionBody txn;

	public static SignedTxnAccessor uncheckedFrom(Transaction validSignedTxn) {
		try {
			return new SignedTxnAccessor(validSignedTxn);
		} catch (Exception impossible) {}
		return null;
	}

	public SignedTxnAccessor(byte[] signedTxnBytes) throws InvalidProtocolBufferException {
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

	public Transaction getSignedTxn4Log() {
		if (signedTxn4Log == null) {
			try {
				signedTxn4Log = signedTxn.toBuilder()
						.setBody(TransactionBody.parseFrom(signedTxn.getBodyBytes()))
						.clearBodyBytes()
						.build();
			} catch (InvalidProtocolBufferException ignore) {}
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
}
