/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.transactions.TxnUtils.asDuration;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTimestamp;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTopicId;
import static com.hedera.services.bdd.suites.HapiSuite.EMPTY_KEY;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;

import com.google.common.base.MoreObjects;
import com.google.protobuf.StringValue;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.node.app.hapi.utils.fee.ConsensusServiceFeeBuilder;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;

public class HapiTopicUpdate extends HapiTxnOp<HapiTopicUpdate> {
    private final String topic;

    private Optional<String> topicMemo = Optional.empty();
    private OptionalLong newExpiry = OptionalLong.empty();
    private OptionalLong newAutoRenewPeriod = OptionalLong.empty();
    private Optional<String> newAdminKeyName = Optional.empty();
    private Optional<Key> newAdminKey = Optional.empty();
    private Optional<String> newSubmitKeyName = Optional.empty();
    private Optional<Key> newSubmitKey = Optional.empty();
    private Optional<String> newAutoRenewAccount = Optional.empty();

    public HapiTopicUpdate(final String topic) {
        this.topic = topic;
    }

    public HapiTopicUpdate topicMemo(final String s) {
        topicMemo = Optional.of(s);
        return this;
    }

    public HapiTopicUpdate adminKey(final String name) {
        newAdminKeyName = Optional.of(name);
        return this;
    }

    public HapiTopicUpdate adminKey(final Key key) {
        newAdminKey = Optional.of(key);
        return this;
    }

    public HapiTopicUpdate submitKey(final String name) {
        newSubmitKeyName = Optional.of(name);
        return this;
    }

    public HapiTopicUpdate submitKey(final Key key) {
        newSubmitKey = Optional.of(key);
        return this;
    }

    public HapiTopicUpdate expiry(final long at) {
        newExpiry = OptionalLong.of(at);
        return this;
    }

    public HapiTopicUpdate autoRenewPeriod(final long secs) {
        newAutoRenewPeriod = OptionalLong.of(secs);
        return this;
    }

    public HapiTopicUpdate autoRenewAccountId(final String id) {
        newAutoRenewAccount = Optional.of(id);
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return ConsensusUpdateTopic;
    }

    @Override
    protected HapiTopicUpdate self() {
        return this;
    }

    @Override
    protected void updateStateOf(final HapiSpec spec) {
        if (actualStatus != ResponseCodeEnum.SUCCESS) {
            return;
        }
        newAdminKey.ifPresent(k -> {
            if (newAdminKey.get() == EMPTY_KEY) {
                spec.registry().removeKey(topic);
            } else {
                spec.registry().saveKey(topic, k);
            }
        });
        newSubmitKey.ifPresent(k -> {
            if (newSubmitKey.get() == EMPTY_KEY) {
                spec.registry().removeKey(submitKeyName());
            } else {
                spec.registry().saveKey(submitKeyName(), k);
            }
        });
        try {
            final TransactionBody txn = CommonUtils.extractTransactionBody(txnSubmitted);
            spec.registry().saveTopicMeta(topic, txn.getConsensusUpdateTopic());
        } catch (final Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(final HapiSpec spec) throws Throwable {
        newAdminKeyName.ifPresent(
                name -> newAdminKey = Optional.of(spec.registry().getKey(name)));
        newSubmitKeyName.ifPresent(
                name -> newSubmitKey = Optional.of(spec.registry().getKey(name)));
        final ConsensusUpdateTopicTransactionBody opBody = spec.txns()
                .<ConsensusUpdateTopicTransactionBody, ConsensusUpdateTopicTransactionBody.Builder>body(
                        ConsensusUpdateTopicTransactionBody.class, b -> {
                            b.setTopicID(asTopicId(topic, spec));
                            topicMemo.ifPresent(memo -> b.setMemo(StringValue.of(memo)));
                            newAdminKey.ifPresent(b::setAdminKey);
                            newSubmitKey.ifPresent(b::setSubmitKey);
                            newExpiry.ifPresent(s -> b.setExpirationTime(asTimestamp(s)));
                            newAutoRenewPeriod.ifPresent(s -> b.setAutoRenewPeriod(asDuration(s)));
                            newAutoRenewAccount.ifPresent(id -> b.setAutoRenewAccount(asId(id, spec)));
                        });
        return b -> b.setConsensusUpdateTopic(opBody);
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        final List<Function<HapiSpec, Key>> signers = new ArrayList<>();
        signers.add(spec -> spec.registry().getKey(effectivePayer(spec)));
        signers.add(
                spec -> spec.registry().hasKey(topic)
                        ? spec.registry().getKey(topic)
                        : Key.getDefaultInstance() // same as no key
                );
        newAdminKey.ifPresent(key -> {
            if (key != EMPTY_KEY) {
                signers.add(ignored -> key);
            }
        });
        newAutoRenewAccount.ifPresent(id -> {
            if (!id.equalsIgnoreCase("0.0.0")) {
                signers.add(spec -> spec.registry().getKey(id));
            }
        });
        return signers;
    }

    @Override
    protected long feeFor(final HapiSpec spec, final Transaction txn, final int numPayerKeys) throws Throwable {
        if (!spec.registry().hasTopicMeta(topic)) {
            return spec.fees().maxFeeTinyBars();
        } else {
            /* Lookup topic metadata saved during creation. */
            final long oldExpiry = spec.registry().getTopicExpiry(topic);
            final ConsensusCreateTopicTransactionBody oldMeta = spec.registry().getTopicMeta(topic);

            /* Computed the increase in RBS due to this update. */
            long tentativeRbsIncrease = 0;
            try {
                final TransactionBody updateTxn = CommonUtils.extractTransactionBody(txn);
                tentativeRbsIncrease = ConsensusServiceFeeBuilder.getUpdateTopicRbsIncrease(
                        updateTxn.getTransactionID().getTransactionValidStart(),
                        oldMeta.getAdminKey(),
                        oldMeta.getSubmitKey(),
                        oldMeta.getMemo(),
                        oldMeta.hasAutoRenewAccount(),
                        Timestamp.newBuilder().setSeconds(oldExpiry).build(),
                        updateTxn.getConsensusUpdateTopic());
            } catch (final Exception impossible) {
                throw new IllegalStateException(impossible);
            }

            /* Create a custom activity metrics calculator based on the rbsIncrease. */
            final long rbsIncrease = tentativeRbsIncrease;
            final FeeCalculator.ActivityMetrics metricsCalc = (txBody, sigUsage) ->
                    ConsensusServiceFeeBuilder.getConsensusUpdateTopicFee(txBody, rbsIncrease, sigUsage);

            /* Return the net fee. */
            return spec.fees().forActivityBasedOp(ConsensusUpdateTopic, metricsCalc, txn, numPayerKeys);
        }
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        final MoreObjects.ToStringHelper helper = super.toStringHelper().add("topic", topic);
        newAutoRenewAccount.ifPresent(id -> helper.add("autoRenewId", id));
        return helper;
    }

    private String submitKeyName() {
        return topic + "Submit";
    }
}
