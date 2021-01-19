package com.hedera.services.bdd.spec.transactions.schedule;

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
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.usage.schedule.ScheduleCreateUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnFactory.bannerWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiScheduleCreate extends HapiTxnOp<HapiScheduleCreate> {
    static final Logger log = LogManager.getLogger(HapiScheduleCreate.class);

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.ScheduleCreate;
    }

    private String schedule;

    private boolean advertiseCreation = false;
    Optional<ByteString> transactionBody = Optional.empty();
    Optional<String> adminKey = Optional.empty();
    Optional<String> payerAccountID = Optional.empty();
    Optional<ByteString[]> sigMap = Optional.empty();

    public HapiScheduleCreate(String schedule) {
        this.schedule = schedule;
    }

    public HapiScheduleCreate advertisingCreation() {
        advertiseCreation = true;
        return this;
    }

    public HapiScheduleCreate transactionBody(ByteString s) {
        transactionBody = Optional.of(s);
        return this;
    }

    public HapiScheduleCreate adminKey(String s) {
        adminKey = Optional.of(s);
        return this;
    }

    public HapiScheduleCreate payer(String s) {
        payerAccountID = Optional.of(s);
        return this;
    }

    public HapiScheduleCreate sigMap(ByteString[] s) {
        sigMap = Optional.of(s);
        return this;
    }

    @Override
    protected HapiScheduleCreate self() {
        return this;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
        ScheduleCreateTransactionBody opBody = spec
                .txns()
                .<ScheduleCreateTransactionBody, ScheduleCreateTransactionBody.Builder>body(
                        ScheduleCreateTransactionBody.class, b -> {
                            transactionBody.ifPresent(b::setTransactionBody);
                            adminKey.ifPresent(k -> b.setAdminKey(spec.registry().getKey(k)));
                            payerAccountID.ifPresent(a -> {
                                var payer = TxnUtils.asId(a, spec);
                                b.setPayerAccountID(payer);
                            });
                            if (sigMap.isPresent()) {
                                var signatureMap = SignatureMap.newBuilder();
                                for (ByteString s : sigMap.get()) {
                                    signatureMap.addSigPair(SignaturePair.newBuilder().setEd25519(s).build());
                                }
                                b.setSigMap(signatureMap);
                            }
                        });
        return b -> b.setScheduleCreate(opBody);
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
        return spec.clients().getScheduleSvcStub(targetNodeFor(spec), useTls)::createSchedule;
    }

    @Override
    protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        return spec.fees().forActivityBasedOp(
                HederaFunctionality.ScheduleCreate, this::usageEstimate, txn, numPayerKeys);
    }

    private FeeData usageEstimate(TransactionBody txn, SigValueObj svo) {
        return ScheduleCreateUsage.newEstimate(txn, suFrom(svo)).get();
    }

    @Override
    protected void updateStateOf(HapiApiSpec spec) throws Throwable {
        if (actualStatus != SUCCESS) {
            return;
        }
        var registry = spec.registry();
        registry.saveScheduleId(schedule, lastReceipt.getScheduleID());

        adminKey.ifPresent(k -> registry.saveAdminKey(schedule, registry.getKey(k)));

        if (advertiseCreation) {
            String banner = "\n\n" + bannerWith(
                    String.format(
                            "Created schedule '%s' with id '0.0.%d'.", schedule, lastReceipt.getScheduleID().getScheduleNum()));
            log.info(banner);
        }
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper = super.toStringHelper()
                .add("schedule", schedule);
        Optional
                .ofNullable(lastReceipt)
                .ifPresent(receipt -> {
                    if (receipt.getScheduleID().getScheduleNum() != 0) {
                        helper.add("schedule", receipt.getScheduleID().getScheduleNum());
                    }
                });
        return helper;
    }

    @Override
    protected List<Function<HapiApiSpec, Key>> defaultSigners() {
        List<Function<HapiApiSpec, Key>> signers = new ArrayList<>(List.of(
                spec -> spec.registry().getKey(effectivePayer(spec))));
        adminKey.ifPresent(k -> signers.add(spec -> spec.registry().getKey(k)));
        return signers;
    }
}
