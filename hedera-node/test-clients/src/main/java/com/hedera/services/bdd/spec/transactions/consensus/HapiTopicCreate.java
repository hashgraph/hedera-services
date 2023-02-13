/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.transactions.TxnFactory.bannerWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asDuration;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.netOf;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.node.app.hapi.utils.fee.ConsensusServiceFeeBuilder;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiTopicCreate extends HapiTxnOp<HapiTopicCreate> {
    static final Logger log = LogManager.getLogger(HapiTopicCreate.class);

    private Key adminKey;
    private final String topic;
    private boolean advertiseCreation = false;
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

    public HapiTopicCreate(final String topic) {
        this.topic = topic;
    }

    public HapiTopicCreate advertisingCreation() {
        advertiseCreation = true;
        return this;
    }

    public HapiTopicCreate topicMemo(final String s) {
        topicMemo = Optional.of(s);
        return this;
    }

    public HapiTopicCreate adminKeyName(final String s) {
        adminKeyName = Optional.of(s);
        return this;
    }

    public HapiTopicCreate submitKeyName(final String s) {
        submitKeyName = Optional.of(s);
        return this;
    }

    public HapiTopicCreate adminKeyShape(final KeyShape shape) {
        adminKeyShape = Optional.of(shape);
        return this;
    }

    public HapiTopicCreate submitKeyShape(final KeyShape shape) {
        submitKeyShape = Optional.of(shape);
        return this;
    }

    public HapiTopicCreate autoRenewAccountId(final String id) {
        autoRenewAccountId = Optional.of(id);
        return this;
    }

    public HapiTopicCreate autoRenewPeriod(final long secs) {
        autoRenewPeriod = OptionalLong.of(secs);
        return this;
    }

    public HapiTopicCreate clearAutoRenewPeriod() {
        this.clearAutoRenewPeriod = true;
        return this;
    }

    @Override
    protected Key lookupKey(final HapiSpec spec, final String name) {
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
    protected Consumer<TransactionBody.Builder> opBodyDef(final HapiSpec spec) throws Throwable {
        genKeysFor(spec);
        final ConsensusCreateTopicTransactionBody opBody =
                spec.txns()
                        .<ConsensusCreateTopicTransactionBody,
                                ConsensusCreateTopicTransactionBody.Builder>
                                body(
                                        ConsensusCreateTopicTransactionBody.class,
                                        b -> {
                                            if (adminKey != null) {
                                                b.setAdminKey(adminKey);
                                            }
                                            topicMemo.ifPresent(b::setMemo);
                                            submitKey.ifPresent(b::setSubmitKey);
                                            autoRenewAccountId.ifPresent(
                                                    id -> b.setAutoRenewAccount(asId(id, spec)));
                                            autoRenewPeriod.ifPresent(
                                                    secs -> b.setAutoRenewPeriod(asDuration(secs)));
                                            if (clearAutoRenewPeriod) {
                                                b.clearAutoRenewPeriod();
                                            }
                                        });
        return b -> b.setConsensusCreateTopic(opBody);
    }

    private void genKeysFor(final HapiSpec spec) {
        if (adminKeyName.isPresent() || adminKeyShape.isPresent()) {
            adminKey = netOf(spec, adminKeyName, adminKeyShape, Optional.of(this::effectiveKeyGen));
        }

        if (submitKeyName.isPresent() || submitKeyShape.isPresent()) {
            submitKey =
                    Optional.of(
                            netOf(
                                    spec,
                                    submitKeyName,
                                    submitKeyShape,
                                    Optional.of(this::effectiveKeyGen)));
        }
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        final List<Function<HapiSpec, Key>> signers =
                new ArrayList<>(List.of(spec -> spec.registry().getKey(effectivePayer(spec))));
        Optional.ofNullable(adminKey).ifPresent(k -> signers.add(ignore -> k));
        autoRenewAccountId.ifPresent(id -> signers.add(spec -> spec.registry().getKey(id)));
        return signers;
    }

    @Override
    protected void updateStateOf(final HapiSpec spec) {
        if (actualStatus != SUCCESS) {
            return;
        }
        spec.registry().saveKey(topic, adminKey);
        submitKey.ifPresent(key -> spec.registry().saveKey(topic + "Submit", key));
        spec.registry().saveTopicId(topic, lastReceipt.getTopicID());
        spec.registry().saveBytes(topic, ByteString.copyFrom(new byte[48]));
        try {
            final TransactionBody txn = CommonUtils.extractTransactionBody(txnSubmitted);
            final long approxConsensusTime =
                    txn.getTransactionID().getTransactionValidStart().getSeconds() + 1;
            spec.registry()
                    .saveTopicMeta(topic, txn.getConsensusCreateTopic(), approxConsensusTime);
        } catch (final Exception impossible) {
            throw new IllegalStateException(impossible);
        }

        if (advertiseCreation) {
            final String banner =
                    "\n\n"
                            + bannerWith(
                                    String.format(
                                            "Created topic '%s' with id '0.0.%d'.",
                                            topic, lastReceipt.getTopicID().getTopicNum()));
            log.info(banner);
        }
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(final HapiSpec spec) {
        return spec.clients().getConsSvcStub(targetNodeFor(spec), useTls)::createTopic;
    }

    @Override
    protected long feeFor(final HapiSpec spec, final Transaction txn, final int numPayerKeys)
            throws Throwable {
        return spec.fees()
                .forActivityBasedOp(
                        ConsensusCreateTopic,
                        ConsensusServiceFeeBuilder::getConsensusCreateTopicFee,
                        txn,
                        numPayerKeys);
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        final MoreObjects.ToStringHelper helper = super.toStringHelper().add("topic", topic);
        autoRenewAccountId.ifPresent(id -> helper.add("autoRenewId", id));
        Optional.ofNullable(lastReceipt)
                .ifPresent(
                        receipt -> {
                            if (receipt.getTopicID().getTopicNum() != 0) {
                                helper.add("created", receipt.getTopicID().getTopicNum());
                            }
                        });
        return helper;
    }

    public long numOfCreatedTopic() {
        return Optional.ofNullable(lastReceipt)
                .map(receipt -> receipt.getTopicID().getTopicNum())
                .orElse(-1L);
    }
}
