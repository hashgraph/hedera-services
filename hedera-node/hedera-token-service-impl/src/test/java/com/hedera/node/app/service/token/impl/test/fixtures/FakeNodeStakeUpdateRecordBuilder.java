/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.fixtures;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.records.NodeStakeUpdateRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Fake Node Stake Update Record Builder
 */
public class FakeNodeStakeUpdateRecordBuilder {
    /**
     * Constructs a {@link FakeNodeStakeUpdateRecordBuilder} instance.
     * @return a {@link FakeNodeStakeUpdateRecordBuilder} instance
     */
    public NodeStakeUpdateRecordBuilder create() {
        return new NodeStakeUpdateRecordBuilder() {
            private String memo;
            private Transaction txn;
            private TransactionBody.DataOneOfType transactionBodyType;

            @Override
            public NodeStakeUpdateRecordBuilder status(@NonNull ResponseCodeEnum status) {
                return null;
            }

            @NonNull
            @Override
            public NodeStakeUpdateRecordBuilder transaction(@NonNull final Transaction txn) {
                this.txn = txn;
                return this;
            }

            @NonNull
            @Override
            public NodeStakeUpdateRecordBuilder memo(@NonNull String memo) {
                this.memo = memo;
                return this;
            }

            @NonNull
            @Override
            public NodeStakeUpdateRecordBuilder transactionBodyType(
                    @NonNull final TransactionBody.DataOneOfType transactionBodyType) {
                this.transactionBodyType = transactionBodyType;
                return this;
            }
        };
    }
}
