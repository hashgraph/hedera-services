/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.transactions.consensus;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTokenId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTopicId;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.ConsensusApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusCryptoFeeScheduleAllowance;
import com.hederahashgraph.api.proto.java.ConsensusTokenFeeScheduleAllowance;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HapiTopicApproveAllowance extends HapiTxnOp<HapiTopicApproveAllowance> {
    private final List<CryptoAllowances> cryptoAllowances = new ArrayList<>();
    private final List<TokenAllowances> tokenAllowances = new ArrayList<>();

    public HapiTopicApproveAllowance() {}

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.ConsensusApproveAllowance;
    }

    @Override
    protected HapiTopicApproveAllowance self() {
        return this;
    }

    public HapiTopicApproveAllowance addCryptoAllowance(
            String owner, String topic, long allowance, long allowancePerMessage) {
        cryptoAllowances.add(CryptoAllowances.from(owner, topic, allowance, allowancePerMessage));
        return this;
    }

    public HapiTopicApproveAllowance addTokenAllowance(
            String owner, String token, String topic, long allowance, long allowancePerMessage) {
        tokenAllowances.add(TokenAllowances.from(owner, token, topic, allowance, allowancePerMessage));
        return this;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        List<ConsensusCryptoFeeScheduleAllowance> callowances = new ArrayList<>();
        List<ConsensusTokenFeeScheduleAllowance> tallowances = new ArrayList<>();
        calculateAllowances(spec, callowances, tallowances);
        ConsensusApproveAllowanceTransactionBody opBody = spec.txns()
                .<ConsensusApproveAllowanceTransactionBody, ConsensusApproveAllowanceTransactionBody.Builder>body(
                        ConsensusApproveAllowanceTransactionBody.class, b -> {
                            b.addAllConsensusCryptoFeeScheduleAllowances(callowances);
                            b.addAllConsensusTokenFeeScheduleAllowances(tallowances);
                        });
        return b -> b.setConsensusApproveAllowance(opBody);
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        return 0;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("cryptoAllowances", cryptoAllowances).add("tokenAllowances", tokenAllowances);
    }

    //    @Override
    //    protected void updateStateOf(HapiSpec spec) {
    //        // No state changes
    //    }

    private void calculateAllowances(
            final HapiSpec spec,
            final List<ConsensusCryptoFeeScheduleAllowance> callowances,
            final List<ConsensusTokenFeeScheduleAllowance> tallowances) {
        for (var entry : cryptoAllowances) {
            final var builder = ConsensusCryptoFeeScheduleAllowance.newBuilder()
                    .setOwner(asId(entry.owner(), spec))
                    .setAmount(entry.amount())
                    .setAmountPerMessage(entry.amountPerMessage())
                    .setTopicId(asTopicId(entry.topic(), spec));
            callowances.add(builder.build());
        }

        for (var entry : tokenAllowances) {
            final var builder = ConsensusTokenFeeScheduleAllowance.newBuilder()
                    .setOwner(asId(entry.owner, spec))
                    .setTokenId(asTokenId(entry.token, spec))
                    .setTopicId(asTopicId(entry.topic, spec))
                    .setAmount(entry.amount)
                    .setAmountPerMessage(entry.amountPerMessage);
            tallowances.add(builder.build());
        }
    }

    private record CryptoAllowances(String owner, String topic, Long amount, Long amountPerMessage) {
        static CryptoAllowances from(String owner, String topic, Long amount, Long amountPerMessage) {
            return new CryptoAllowances(owner, topic, amount, amountPerMessage);
        }
    }

    private record TokenAllowances(String owner, String token, String topic, Long amount, Long amountPerMessage) {
        static TokenAllowances from(String owner, String token, String topic, Long amount, Long amountPerMessage) {
            return new TokenAllowances(owner, token, topic, amount, amountPerMessage);
        }
    }
}
