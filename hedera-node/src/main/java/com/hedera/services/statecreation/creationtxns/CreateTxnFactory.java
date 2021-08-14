package com.hedera.services.statecreation.creationtxns;

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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.statecreation.creationtxns.utils.KeyFactory;
import com.hedera.services.statecreation.creationtxns.utils.SigFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.statecreation.creationtxns.utils.TempUtils.asAccount;


public abstract class CreateTxnFactory<T extends CreateTxnFactory<T>> {
	public static final String DEFAULT_MEMO = "default memo.";
	public static final String DEFAULT_NODE_ID = "0.0.3";
	public static final AccountID DEFAULT_NODE = asAccount(DEFAULT_NODE_ID);
	public static final String DEFAULT_PAYER_ID = "0.0.2";
	public static final String MASTER_PAYER_ID = "0.0.50";
	public static final AccountID DEFAULT_PAYER = asAccount(DEFAULT_PAYER_ID);
	public static final Instant DEFAULT_VALID_START = Instant.now();
	public static final Integer DEFAULT_VALID_DURATION = 60;
	public static final SigFactory DEFAULT_SIG_FACTORY = new SigFactory();

	protected KeyFactory keyFactory = KeyFactory.getDefaultInstance();
	protected FeeBuilder fees = new FeeBuilder();

	String memo = DEFAULT_MEMO;
	String node = DEFAULT_NODE_ID;
	String payer = DEFAULT_PAYER_ID;
	boolean skipTxnId = false;
	Instant start = DEFAULT_VALID_START;
	Integer validDuration = DEFAULT_VALID_DURATION;
	SigFactory sigFactory = DEFAULT_SIG_FACTORY;
	Optional<Long> customFee = Optional.empty();

	private List<Key> keys;

	protected abstract T self();

	protected abstract long feeFor(Transaction signedTxn, int numPayerKeys);

	protected abstract void customizeTxn(TransactionBody.Builder txn);

	public Transaction get() throws Throwable {
		Transaction provisional = signed(signableTxn(customFee.orElse(0L)));
		return customFee.isPresent()
				? provisional
				: signed(signableTxn(feeFor(provisional, keys.size())));
	}

	private Transaction.Builder signableTxn(long fee) {
		TransactionBody.Builder txn = baseTxn();
		customizeTxn(txn);
		txn.setTransactionFee(fee);
		return Transaction.newBuilder().setBodyBytes(ByteString.copyFrom(txn.build().toByteArray()));
	}

	private Transaction signed(Transaction.Builder txnWithSigs) throws Throwable {
		KeyPairObj keyPairObj = KeyFactory.genesisKeyPair;
		Key genKey = KeyFactory.asPublicKey(keyPairObj.getPublicKeyAbyteStr());
		keys = Collections.singletonList(genKey);

		return sigFactory.signWithSimpleKey(txnWithSigs, keys, keyFactory);
	}

	private TransactionBody.Builder baseTxn() {
		TransactionBody.Builder txn = TransactionBody.newBuilder()
				.setNodeAccountID(asAccount(node))
				.setTransactionValidDuration(validDuration())
				.setMemo(memo);
		if (!skipTxnId) {
			txn.setTransactionID(txnId());
		}
		return txn;
	}

	private TransactionID txnId() {
		return TransactionID.newBuilder()
				.setAccountID(asAccount(payer))
				.setTransactionValidStart(validStart())
				.build();
	}

	private Timestamp validStart() {
		Instant now = Instant.now();
		return Timestamp.newBuilder()
				.setSeconds(now.getEpochSecond())
				.setNanos(now.getNano())
				.build();
	}

	private Duration validDuration() {
		return Duration.newBuilder().setSeconds(validDuration).build();
	}

	public T payer(String payer) {
		this.payer = payer;
		return self();
	}

	public T fee(long amount) {
		customFee = Optional.of(amount);
		return self();
	}

	public T keyFactory(KeyFactory keyFactory) {
		this.keyFactory = keyFactory;
		return self();
	}

	public T txnValidStart(Timestamp at) {
		start = Instant.ofEpochSecond(at.getSeconds(), at.getNanos());
		return self();
	}
}
