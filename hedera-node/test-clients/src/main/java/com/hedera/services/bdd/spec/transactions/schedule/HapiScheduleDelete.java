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

package com.hedera.services.bdd.spec.transactions.schedule;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiScheduleDelete extends HapiTxnOp<HapiScheduleDelete> {
    static final Logger log = LogManager.getLogger(HapiScheduleDelete.class);

    private String schedule;

    public HapiScheduleDelete(String schedule) {
        this.schedule = schedule;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.ScheduleDelete;
    }

    @Override
    protected HapiScheduleDelete self() {
        return this;
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        try {
            final ScheduleInfo info = ScheduleFeeUtils.lookupInfo(spec, schedule, loggingOff);
            FeeCalculator.ActivityMetrics metricsCalc = (_txn, svo) -> scheduleOpsUsage.scheduleDeleteUsage(
                    _txn, suFrom(svo), info.getExpirationTime().getSeconds());
            return spec.fees().forActivityBasedOp(HederaFunctionality.ScheduleDelete, metricsCalc, txn, numPayerKeys);
        } catch (Throwable ignore) {
            return HapiSuite.ONE_HBAR;
        }
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        var sId = TxnUtils.asScheduleId(schedule, spec);
        ScheduleDeleteTransactionBody opBody = spec.txns()
                .<ScheduleDeleteTransactionBody, ScheduleDeleteTransactionBody.Builder>body(
                        ScheduleDeleteTransactionBody.class, b -> {
                            b.setScheduleID(sId);
                        });
        return b -> b.setScheduleDelete(opBody);
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        return List.of(spec -> spec.registry().getKey(effectivePayer(spec)));
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiSpec spec) {
        return spec.clients().getScheduleSvcStub(targetNodeFor(spec), useTls)::deleteSchedule;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper = super.toStringHelper().add("schedule", schedule);
        return helper;
    }
}
