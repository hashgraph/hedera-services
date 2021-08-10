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

//import com.hedera.test.factories.keys.KeyTree;
import com.hedera.services.statecreation.creationtxns.utils.KeyTree;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.statecreation.creationtxns.utils.IdUtils.asAccount;
import static com.hedera.services.statecreation.creationtxns.utils.NodeFactory.ed25519;


public class TopicCreateTxnFactory extends CreateTxnFactory<TopicCreateTxnFactory> {
	public static final KeyTree SIMPLE_TOPIC_ADMIN_KEY = KeyTree.withRoot(ed25519());
	public static final Duration DEFAULT_AUTO_RENEW_PERIOD = Duration.newBuilder().setSeconds(30 * 86_400L).build();

	private String memo = null;
	private KeyTree adminKey = null;
	private KeyTree submitKey = null;
	private Duration autoRenewPeriod = DEFAULT_AUTO_RENEW_PERIOD;
	private String autoRenewAccountId = null;

	private TopicCreateTxnFactory() {}
	public static TopicCreateTxnFactory newSignedConsensusCreateTopic() {
		return new TopicCreateTxnFactory();
	}

	@Override
	protected TopicCreateTxnFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder transactionBody) {
		ConsensusCreateTopicTransactionBody.Builder op = ConsensusCreateTopicTransactionBody.newBuilder();
		if (null != memo) {
			op.setMemo(memo);
		}
		if (null != adminKey) {
			op.setAdminKey(adminKey.asKey(keyFactory));
		}
		if (null != submitKey) {
			op.setSubmitKey(submitKey.asKey(keyFactory));
		}
		if (null != autoRenewPeriod) {
			op.setAutoRenewPeriod(autoRenewPeriod);
		}
		if (null != autoRenewAccountId) {
			op.setAutoRenewAccount(asAccount(autoRenewAccountId));
		}
		transactionBody.setConsensusCreateTopic(op);
	}

	public TopicCreateTxnFactory memo(String memo) {
		this.memo = memo;
		return this;
	}

	public TopicCreateTxnFactory adminKey(KeyTree adminKey) {
		this.adminKey = adminKey;
		return this;
	}

	public TopicCreateTxnFactory submitKey(KeyTree submitKey) {
		this.submitKey = submitKey;
		return this;
	}

	public TopicCreateTxnFactory autoRenewPeriod(Duration autoRenewPeriod) {
		this.autoRenewPeriod = autoRenewPeriod;
		return this;
	}

	public TopicCreateTxnFactory autoRenewAccountId(String autoRenewAccountId) {
		this.autoRenewAccountId = autoRenewAccountId;
		return this;
	}
}
