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
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.fee.ConsensusServiceFeeBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTopicId;

public class HapiTopicDelete extends HapiTxnOp<HapiTopicDelete> {
	private Optional<String> topic = Optional.empty();
	private Optional<Function<HapiApiSpec, TopicID>> topicFn = Optional.empty();

	public HapiTopicDelete(String topic) {
		this.topic = Optional.ofNullable(topic);
	}

	public HapiTopicDelete(Function<HapiApiSpec, TopicID> topicFn) {
		this.topicFn = Optional.of(topicFn);
	}

	@Override
	public HederaFunctionality type() {
		return ConsensusDeleteTopic;
	}

	@Override
	protected HapiTopicDelete self() {
		return this;
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		TopicID id = resolveTopicId(spec);
		ConsensusDeleteTopicTransactionBody opBody = spec
				.txns()
				.<ConsensusDeleteTopicTransactionBody, ConsensusDeleteTopicTransactionBody.Builder>body(
						ConsensusDeleteTopicTransactionBody.class, b -> {
							b.setTopicID(id);
						});
		return b -> b.setConsensusDeleteTopic(opBody);
	}

	private TopicID resolveTopicId(HapiApiSpec spec) {
		if (topicFn.isPresent()) {
			TopicID topicID = topicFn.get().apply(spec);
			topic = Optional.of(HapiPropertySource.asTopicString(topicID));
			return topicID;
		}
		if (topic.isPresent()) {
			return asTopicId(topic.get(), spec);
		}
		return TopicID.getDefaultInstance();
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getConsSvcStub(targetNodeFor(spec), useTls)::deleteTopic;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				ConsensusDeleteTopic, ConsensusServiceFeeBuilder::getConsensusDeleteTopicFee, txn, numPayerKeys);
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		List<Function<HapiApiSpec, Key>> signers =
				new ArrayList<>(List.of(spec -> spec.registry().getKey(effectivePayer(spec))));
		// Key may not be present when testing for failure cases.
		topic.ifPresent(topic -> signers.add(spec -> {
			if (spec.registry().hasKey(topic)) {
				return spec.registry().getKey(topic);
			} else {
				return Key.getDefaultInstance();  // = no key
			}
		}));

		return signers;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper();
		topic.ifPresent(n -> helper.add("topic", n));
		Optional.ofNullable(lastReceipt)
				.ifPresent(receipt -> helper.add("deleted", receipt.getTopicID().getTopicNum()));
		return helper;
	}
}
