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
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.fee.ConsensusServiceFeeBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTopicId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTransactionID;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;

public class HapiMessageSubmit extends HapiTxnOp<HapiMessageSubmit> {
	private static long totalMsgSubmitted = 0;
	private Optional<String> topic = Optional.empty();
	private Optional<Function<HapiApiSpec, TopicID>> topicFn = Optional.empty();
	private Optional<ByteString> message = Optional.empty();
	private OptionalInt totalChunks = OptionalInt.empty();
	private OptionalInt chunkNumber = OptionalInt.empty();
	private Optional<String> initialTransactionPayer = Optional.empty();
	private Optional<TransactionID> initialTransactionID = Optional.empty();
	private boolean clearMessage = false;

	public HapiMessageSubmit(String topic) {
		this.topic = Optional.ofNullable(topic);
	}

	public HapiMessageSubmit(Function<HapiApiSpec, TopicID> topicFn) {
		this.topicFn = Optional.of(topicFn);
	}

	@Override
	public HederaFunctionality type() {
		return ConsensusSubmitMessage;
	}

	@Override
	protected HapiMessageSubmit self() {
		return this;
	}

	public Optional<String> getTopic() {
		return topic;
	}

	public Optional<ByteString> getMessage() {
		return message;
	}

	public HapiMessageSubmit defaultName() {
		totalMsgSubmitted++;
		super.via("submitMessage" + totalMsgSubmitted);
		return this;
	}

	public HapiMessageSubmit message(ByteString s) {
		message = Optional.of(s);
		return this;
	}

	public HapiMessageSubmit message(byte[] s) {
		message(ByteString.copyFrom(s));
		return this;
	}

	public HapiMessageSubmit message(String s) {
		message(s.getBytes());
		return this;
	}

	public HapiMessageSubmit clearMessage() {
		clearMessage = true;
		return this;
	}

	public HapiMessageSubmit chunkInfo(int totalChunks, int chunkNumber) {
		this.totalChunks = OptionalInt.of(totalChunks);
		this.chunkNumber = OptionalInt.of(chunkNumber);
		return this;
	}

	public HapiMessageSubmit chunkInfo(int totalChunks, int chunkNumber, String initialTransactionPayer) {
		this.initialTransactionPayer = Optional.of(initialTransactionPayer);
		return chunkInfo(totalChunks, chunkNumber);
	}

	public HapiMessageSubmit chunkInfo(int totalChunks, int chunkNumber, TransactionID initialTransactionID) {
		this.initialTransactionID = Optional.of(initialTransactionID);
		return chunkInfo(totalChunks, chunkNumber);
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		TopicID id = resolveTopicId(spec);
		ConsensusSubmitMessageTransactionBody opBody = spec
				.txns()
				.<ConsensusSubmitMessageTransactionBody, ConsensusSubmitMessageTransactionBody.Builder>body(
						ConsensusSubmitMessageTransactionBody.class, b -> {
							b.setTopicID(id);
							message.ifPresent(m -> b.setMessage(m));
							if (clearMessage) {
								b.clearMessage();
							}
							if (totalChunks.isPresent() && chunkNumber.isPresent()) {
								ConsensusMessageChunkInfo chunkInfo = ConsensusMessageChunkInfo
										.newBuilder()
										.setInitialTransactionID(initialTransactionID.orElse(asTransactionID(spec,
												initialTransactionPayer.isPresent() ? initialTransactionPayer : payer)))
										.setTotal(totalChunks.getAsInt())
										.setNumber(chunkNumber.getAsInt())
										.build();
								b.setChunkInfo(chunkInfo);
								spec.registry().saveTimestamp(txnName,
										chunkInfo.getInitialTransactionID().getTransactionValidStart());
							}
						});
		return b -> b.setConsensusSubmitMessage(opBody);
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
	protected Function<HapiApiSpec, List<Key>> variableDefaultSigners() {
		return spec -> {
			if (topic.isPresent() && spec.registry().hasKey(topic.get() + "Submit")) {
				return List.of(spec.registry().getKey(topic.get() + "Submit"));
			}
			return Collections.emptyList();
		};
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getConsSvcStub(targetNodeFor(spec), useTls)::submitMessage;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				ConsensusSubmitMessage, ConsensusServiceFeeBuilder::getConsensusSubmitMessageFee, txn, numPayerKeys);
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("topic", topic.orElse("<not set>"))
				.add("message", message);
		return helper;
	}
}
