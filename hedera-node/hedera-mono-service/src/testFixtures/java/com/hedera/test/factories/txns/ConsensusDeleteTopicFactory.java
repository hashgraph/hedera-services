/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.test.factories.txns;

import static com.hedera.test.utils.IdUtils.asTopic;

import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class ConsensusDeleteTopicFactory extends SignedTxnFactory<ConsensusDeleteTopicFactory> {
    private String topicId;

    private ConsensusDeleteTopicFactory(String topicId) {
        this.topicId = topicId;
    }

    public static ConsensusDeleteTopicFactory newSignedConsensusDeleteTopic(String topicId) {
        return new ConsensusDeleteTopicFactory(topicId);
    }

    @Override
    protected ConsensusDeleteTopicFactory self() {
        return this;
    }

    @Override
    protected long feeFor(Transaction signedTxn, int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(TransactionBody.Builder transactionBody) {
        ConsensusDeleteTopicTransactionBody.Builder op =
                ConsensusDeleteTopicTransactionBody.newBuilder();
        if (null != topicId) {
            op.setTopicID(asTopic(topicId));
        }
        transactionBody.setConsensusDeleteTopic(op);
    }

    public ConsensusDeleteTopicFactory topicId(String topicId) {
        this.topicId = topicId;
        return this;
    }
}
