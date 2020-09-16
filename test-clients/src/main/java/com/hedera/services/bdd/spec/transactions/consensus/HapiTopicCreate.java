package com.hedera.services.bdd.spec.transactions.consensus;

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

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.fee.ConsensusServiceFeeBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asDuration;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.netOf;

public class HapiTopicCreate extends HapiTxnOp<HapiTopicCreate> {
	private Key adminKey;
	private String topic;
	private Optional<Key> submitKey = Optional.empty();
	private OptionalLong autoRenewPeriod = OptionalLong.empty();
	private Optional<String> topicMemo = Optional.empty();
	private Optional<String> adminKeyName = Optional.empty();
	private Optional<String> submitKeyName = Optional.empty();
	private Optional<String> autoRenewAccountId = Optional.empty();
	private Optional<KeyShape> adminKeyShape = Optional.empty();
	private Optional<KeyShape> submitKeyShape = Optional.empty();

	/** For some test we need the capability to build transaction has no autoRenewPeiord */
	private boolean clearAutoRenewPeriod = false;

	public HapiTopicCreate(String topic) {
		this.topic = topic;
	}

	public HapiTopicCreate topicMemo(String s) {
		topicMemo = Optional.of(s);
		return this;
	}

	public HapiTopicCreate adminKeyName(String s) {
		adminKeyName = Optional.of(s);
		return this;
	}

	public HapiTopicCreate submitKeyName(String s) {
		submitKeyName = Optional.of(s);
		return this;
	}

	public HapiTopicCreate adminKeyShape(KeyShape shape) {
		adminKeyShape = Optional.of(shape);
		return this;
	}

	public HapiTopicCreate submitKeyShape(KeyShape shape) {
		submitKeyShape = Optional.of(shape);
		return this;
	}

	public HapiTopicCreate autoRenewAccountId(String id) {
		autoRenewAccountId = Optional.of(id);
		return this;
	}

	public HapiTopicCreate autoRenewPeriod(long secs) {
		autoRenewPeriod = OptionalLong.of(secs);
		return this;
	}

	public HapiTopicCreate clearAutoRenewPeriod() {
		this.clearAutoRenewPeriod = true;
		return this;
	}

	@Override
	protected Key lookupKey(HapiApiSpec spec, String name) {
		if (submitKey.isPresent() && (topic + "Submit").equals(name)) {
			return submitKey.get();
		} else {
			return name.equals(topic) ? adminKey : spec.registry().getKey(name);
		}
	}

	@Override
	public HederaFunctionality type() {
		return ConsensusCreateTopic;
	}

	@Override
	protected HapiTopicCreate self() {
		return this;
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		genKeysFor(spec);
		ConsensusCreateTopicTransactionBody opBody = spec
				.txns()
				.<ConsensusCreateTopicTransactionBody, ConsensusCreateTopicTransactionBody.Builder>body(
						ConsensusCreateTopicTransactionBody.class, b -> {
							if (adminKey != null) {
								b.setAdminKey(adminKey);
							}
							topicMemo.ifPresent(b::setMemo);
							submitKey.ifPresent(b::setSubmitKey);
							autoRenewAccountId.ifPresent(id -> b.setAutoRenewAccount(asId(id, spec)));
							autoRenewPeriod.ifPresent(secs -> b.setAutoRenewPeriod(asDuration(secs)));
							if (clearAutoRenewPeriod) {
								b.clearAutoRenewPeriod();
							}
						});
		return b -> b.setConsensusCreateTopic(opBody);
	}

	private void genKeysFor(HapiApiSpec spec) {
		if (adminKeyName.isPresent() || adminKeyShape.isPresent()) {
			adminKey = netOf(spec, adminKeyName, adminKeyShape, Optional.of(this::effectiveKeyGen));
		}

		if (submitKeyName.isPresent() || submitKeyShape.isPresent()) {
			submitKey = Optional.of(netOf(spec, submitKeyName, submitKeyShape, Optional.of(this::effectiveKeyGen)));
		}
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		List<Function<HapiApiSpec, Key>> signers =
				new ArrayList<>(List.of(spec -> spec.registry().getKey(effectivePayer(spec))));
		Optional.ofNullable(adminKey).ifPresent(k -> signers.add(ignore -> k));
		autoRenewAccountId.ifPresent(id -> signers.add(spec -> spec.registry().getKey(id)));
		return signers;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
		if (actualStatus != SUCCESS) {
			return;
		}
		spec.registry().saveKey(topic, adminKey);
		submitKey.ifPresent(key -> spec.registry().saveKey(topic + "Submit", key));
		spec.registry().saveTopicId(topic, lastReceipt.getTopicID());
		spec.registry().saveBytes(topic, ByteString.copyFrom(new byte[48]));
		try {
			TransactionBody txn = TransactionBody.parseFrom(txnSubmitted.getBodyBytes());
			long approxConsensusTime = txn.getTransactionID().getTransactionValidStart().getSeconds() + 1;
			spec.registry().saveTopicMeta(topic, txn.getConsensusCreateTopic(), approxConsensusTime);
		} catch (Exception impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getConsSvcStub(targetNodeFor(spec), useTls)::createTopic;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				ConsensusCreateTopic, ConsensusServiceFeeBuilder::getConsensusCreateTopicFee, txn, numPayerKeys);
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper().add("topic", topic);
		autoRenewAccountId.ifPresent(id -> helper.add("autoRenewId", id));
		Optional
				.ofNullable(lastReceipt)
				.ifPresent(receipt -> {
					if (receipt.getTopicID().getTopicNum() != 0) {
						helper.add("created", receipt.getTopicID().getTopicNum());
					}
				});
		return helper;
	}

	public long numOfCreatedTopic() {
		return Optional
				.ofNullable(lastReceipt)
				.map(receipt -> receipt.getTopicID().getTopicNum())
				.orElse(-1L);
	}
}
