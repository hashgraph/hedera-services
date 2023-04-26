/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.transactions.TxnUtils.asScheduleId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate.correspondingScheduledTxnId;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.infrastructure.RegistryNotFound;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

public class HapiScheduleSign extends HapiTxnOp<HapiScheduleSign> {
    private final String schedule;
    private List<String> signatories = Collections.emptyList();

    @Nullable
    private List<Key> explicitSigners = null;

    private boolean ignoreMissing = false;
    private boolean saveScheduledTxnId = false;

    public HapiScheduleSign(String schedule) {
        this.schedule = schedule;
    }

    public HapiScheduleSign alsoSigningWith(String... keys) {
        signatories = List.of(keys);
        return this;
    }

    public HapiScheduleSign alsoSigningWithExplicit(final List<Key> keys) {
        explicitSigners = keys;
        return this;
    }

    public HapiScheduleSign ignoreIfMissing() {
        ignoreMissing = true;
        return this;
    }

    public HapiScheduleSign savingScheduledTxnId() {
        saveScheduledTxnId = true;
        return this;
    }

    @Override
    protected HapiScheduleSign self() {
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return ScheduleSign;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        ScheduleSignTransactionBody opBody = spec.txns()
                .<ScheduleSignTransactionBody, ScheduleSignTransactionBody.Builder>body(
                        ScheduleSignTransactionBody.class, b -> {
                            ScheduleID id;
                            try {
                                id = asScheduleId(schedule, spec);
                                b.setScheduleID(id);
                            } catch (RegistryNotFound e) {
                                if (!ignoreMissing) {
                                    throw e;
                                }
                            }
                        });
        return b -> b.setScheduleSign(opBody);
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiSpec spec) {
        return spec.clients().getScheduleSvcStub(targetNodeFor(spec), useTls)::signSchedule;
    }

    @Override
    protected void updateStateOf(HapiSpec spec) throws Throwable {
        if (actualStatus != SUCCESS) {
            return;
        }
        if (saveScheduledTxnId) {
            spec.registry().saveTxnId(correspondingScheduledTxnId(schedule), lastReceipt.getScheduledTransactionID());
        }
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        try {
            final ScheduleInfo info = ScheduleFeeUtils.lookupInfo(spec, schedule, loggingOff);
            FeeCalculator.ActivityMetrics metricsCalc = (_txn, svo) -> scheduleOpsUsage.scheduleSignUsage(
                    _txn, suFrom(svo), info.getExpirationTime().getSeconds());
            return spec.fees().forActivityBasedOp(HederaFunctionality.ScheduleSign, metricsCalc, txn, numPayerKeys);
        } catch (Throwable ignore) {
            return HapiSuite.ONE_HBAR;
        }
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        final var signers = new ArrayList<Function<HapiSpec, Key>>();
        signers.add(spec -> spec.registry().getKey(effectivePayer(spec)));
        if (explicitSigners != null) {
            explicitSigners.forEach(key -> signers.add(spec -> key));
        } else {
            for (String added : signatories) {
                signers.add(spec -> spec.registry().getKey(added));
            }
        }
        return signers;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper =
                super.toStringHelper().add("schedule", schedule).add("signers", signatories);
        return helper;
    }
}
