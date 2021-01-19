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
import com.hedera.services.usage.schedule.ScheduleSignUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;

public class HapiScheduleSign extends HapiTxnOp<HapiScheduleSign> {
    static final Logger log = LogManager.getLogger(HapiScheduleSign.class);

    private String schedule;

    Optional<ByteString[]> sigMap = Optional.empty();

    public HapiScheduleSign(String schedule) {
        this.schedule = schedule;
    }

    public HapiScheduleSign sigMap(ByteString[] s) {
        sigMap = Optional.of(s);
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.ScheduleSign;
    }

    @Override
    protected HapiScheduleSign self() {
        return this;
    }

    @Override
    protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        return spec.fees().forActivityBasedOp(
                HederaFunctionality.ScheduleSign, this::usageEstimate, txn, numPayerKeys);
    }

    private FeeData usageEstimate(TransactionBody txn, SigValueObj svo) {
        return ScheduleSignUsage.newEstimate(txn, suFrom(svo)).get();
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
        var tId = TxnUtils.asScheduleId(schedule, spec);
        ScheduleSignTransactionBody opBody = spec
                .txns()
                .<ScheduleSignTransactionBody, ScheduleSignTransactionBody.Builder>body(
                        ScheduleSignTransactionBody.class, b -> {
                            b.setScheduleID(tId);
                            if (sigMap.isPresent()) {
                                var signatureMap = SignatureMap.newBuilder();
                                for (ByteString s : sigMap.get()) {
                                    signatureMap.addSigPair(SignaturePair.newBuilder().setEd25519(s).build());
                                }
                                b.setSigMap(signatureMap);
                            }
                        });
        return b -> b.setScheduleSign(opBody);
    }

    @Override
    protected List<Function<HapiApiSpec, Key>> defaultSigners() {
        return List.of(
                spec -> spec.registry().getKey(effectivePayer(spec)));
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
        return spec.clients().getScheduleSvcStub(targetNodeFor(spec), useTls)::signSchedule;
    }

    @Override
    protected void updateStateOf(HapiApiSpec spec) {
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper = super.toStringHelper()
                .add("schedule", schedule);
        return helper;
    }
}
