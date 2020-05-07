package com.hedera.services.bdd.spec.transactions;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.KeyFactory;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.*;
import static com.hedera.services.bdd.spec.HapiApiSpec.UTF8Mode.TRUE;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class TxnFactory {
	KeyFactory keys;
	HapiSpecSetup setup;

	private static final double TXN_ID_SAMPLE_PROBABILITY = 1.0 / 500;
	AtomicReference<TransactionID> sampleTxnId = new AtomicReference<>(TransactionID.getDefaultInstance());
	SplittableRandom r = new SplittableRandom();

	public TxnFactory(HapiSpecSetup setup, KeyFactory keys) {
		this.keys = keys;
		this.setup = setup;
	}

	public Transaction.Builder getReadyToSign(Consumer<TransactionBody.Builder> spec) {
		Consumer<TransactionBody.Builder> composedBodySpec = defaultBodySpec().andThen(spec);
		TransactionBody.Builder bodyBuilder = TransactionBody.newBuilder();
		composedBodySpec.accept(bodyBuilder);
		return Transaction.newBuilder()
			.setBodyBytes(ByteString.copyFrom(bodyBuilder.build().toByteArray()));
	}

	public TransactionID sampleRecentTxnId() {
		return sampleTxnId.get();
	}

	public TransactionID defaultTransactionID() {
		return TransactionID.newBuilder()
				.setTransactionValidStart(defaultTimestampPlusSecs(setup.txnStartOffsetSecs()))
				.setAccountID(setup.defaultPayer()).build();
	}

	public Consumer<TransactionBody.Builder> defaultBodySpec() {
		TransactionID defaultTxnId = defaultTransactionID();
		if (r.nextDouble() < TXN_ID_SAMPLE_PROBABILITY) {
			sampleTxnId.set(defaultTxnId);
		}

		final var memoToUse = (setup.isMemoUTF8() == TRUE ) ? setup.defaultUTF8memo() : setup.defaultMemo();

		return builder -> builder
				.setTransactionID(defaultTxnId)
				.setMemo(memoToUse)
				.setTransactionFee(setup.defaultFee())
				.setTransactionValidDuration(setup.defaultValidDuration())
				.setNodeAccountID(setup.defaultNode());
	}

	public <T, B extends Message.Builder> T body(Class<T> tClass, Consumer<B> def) throws Throwable {
		Method newBuilder = tClass.getMethod("newBuilder");
		B opBuilder = (B)newBuilder.invoke(null);
		String defaultBodyMethod = String.format("defaultDef_%s", tClass.getSimpleName());
		Method defaultBody = this.getClass().getMethod(defaultBodyMethod);
		((Consumer<B>)defaultBody.invoke(this)).andThen(def).accept(opBuilder);
		return (T)opBuilder.build();
	}

	public Consumer<ConsensusSubmitMessageTransactionBody.Builder> defaultDef_ConsensusSubmitMessageTransactionBody() {
		return builder -> {
			builder.setMessage(ByteString.copyFrom(setup.defaultConsensusMessage().getBytes()));
		};
	}

	public Consumer<ConsensusUpdateTopicTransactionBody.Builder> defaultDef_ConsensusUpdateTopicTransactionBody() {
		return builder -> {};
	}

	public Consumer<ConsensusCreateTopicTransactionBody.Builder> defaultDef_ConsensusCreateTopicTransactionBody() {
		return builder -> {
			builder.setAutoRenewPeriod(setup.defaultAutoRenewPeriod());
		};
	}

	public Consumer<ConsensusDeleteTopicTransactionBody.Builder> defaultDef_ConsensusDeleteTopicTransactionBody() {
		return builder -> {};
	}

	public Consumer<FreezeTransactionBody.Builder> defaultDef_FreezeTransactionBody() {
		return builder -> {};
	}

	public Consumer<FileDeleteTransactionBody.Builder> defaultDef_FileDeleteTransactionBody() {
		return builder -> {};
	}

	public Consumer<ContractDeleteTransactionBody.Builder> defaultDef_ContractDeleteTransactionBody() {
		return builder -> {
			builder.setTransferAccountID(setup.defaultTransfer());
		};
	}

	public Consumer<ContractUpdateTransactionBody.Builder> defaultDef_ContractUpdateTransactionBody() {
		return builder -> {};
	}

	public Consumer<ContractCallTransactionBody.Builder> defaultDef_ContractCallTransactionBody() {
		return builder -> builder
				.setGas(setup.defaultCallGas());
	}

	public Consumer<ContractCreateTransactionBody.Builder> defaultDef_ContractCreateTransactionBody() {
		return builder -> builder
				.setAutoRenewPeriod(setup.defaultAutoRenewPeriod())
				.setGas(setup.defaultCreateGas())
				.setInitialBalance(setup.defaultContractBalance())
				.setMemo(setup.defaultMemo())
				.setShardID(setup.defaultShard())
				.setRealmID(setup.defaultRealm())
				.setProxyAccountID(setup.defaultProxy());
	}

	public Consumer<FileCreateTransactionBody.Builder> defaultDef_FileCreateTransactionBody() {
		return builder -> builder
				.setRealmID(setup.defaultRealm())
				.setShardID(setup.defaultShard())
				.setContents(ByteString.copyFrom(setup.defaultFileContents()))
				.setExpirationTime(defaultExpiry());
	}

	public Consumer<FileAppendTransactionBody.Builder> defaultDef_FileAppendTransactionBody() {
		return builder -> builder
				.setContents(ByteString.copyFrom(setup.defaultFileContents()));
	}

	public Consumer<FileUpdateTransactionBody.Builder> defaultDef_FileUpdateTransactionBody() {
		return builder -> {};
	}

	private Timestamp defaultExpiry() {
		return expiryGiven(setup.defaultExpirationSecs());
	}

	public static Timestamp expiryGiven(long lifetimeSecs) {
		Instant expiry = Instant.now(Clock.systemUTC()).plusSeconds(lifetimeSecs);
		return Timestamp.newBuilder().setSeconds(expiry.getEpochSecond()).setNanos(expiry.getNano()).build();
	}

	public Consumer<CryptoDeleteTransactionBody.Builder> defaultDef_CryptoDeleteTransactionBody() {
		return builder -> builder
				.setTransferAccountID(setup.defaultTransfer());
	}

	public Consumer<SystemDeleteTransactionBody.Builder> defaultDef_SystemDeleteTransactionBody() {
		return builder -> {};
	}

	public Consumer<SystemUndeleteTransactionBody.Builder> defaultDef_SystemUndeleteTransactionBody() {
		return builder -> {};
	}

	public Consumer<CryptoCreateTransactionBody.Builder> defaultDef_CryptoCreateTransactionBody() {
		return builder -> builder
				.setInitialBalance(setup.defaultBalance())
				.setAutoRenewPeriod(setup.defaultAutoRenewPeriod())
				.setReceiveRecordThreshold(setup.defaultReceiveThreshold())
				.setSendRecordThreshold(setup.defaultSendThreshold())
				.setProxyAccountID(setup.defaultProxy())
				.setReceiverSigRequired(setup.defaultReceiverSigRequired());
	}

	public Consumer<CryptoUpdateTransactionBody.Builder> defaultDef_CryptoUpdateTransactionBody() {
		return builder -> builder
				.setAutoRenewPeriod(setup.defaultAutoRenewPeriod());
	}

	public Consumer<CryptoTransferTransactionBody.Builder> defaultDef_CryptoTransferTransactionBody() {
		return builder -> {};
	}
}
