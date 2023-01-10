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
package com.hedera.services.bdd.spec.infrastructure.providers.ops.schedule;

import static com.hedera.services.bdd.spec.infrastructure.providers.ops.schedule.RandomSchedule.STABLE_RECEIVER;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RandomScheduleSign implements OpProvider {
    static final Logger log = LogManager.getLogger(RandomScheduleSign.class);

    public static final int DEFAULT_CEILING_NUM = 10_000;

    private int ceilingNum = DEFAULT_CEILING_NUM;

    private final RegistrySourcedNameProvider<ScheduleID> schedules;
    private final RegistrySourcedNameProvider<AccountID> accounts;

    private final ResponseCodeEnum[] permissibleOutcomes =
            standardOutcomesAnd(SOME_SIGNATURES_WERE_INVALID, INVALID_SCHEDULE_ID);

    public RandomScheduleSign(
            RegistrySourcedNameProvider<ScheduleID> schedules,
            RegistrySourcedNameProvider<AccountID> accounts) {
        this.schedules = schedules;
        this.accounts = accounts;
    }

    public RandomScheduleSign ceiling(int n) {
        ceilingNum = n;
        return this;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        var schedulesQualifying = schedules.getQualifying();
        if (schedulesQualifying.isEmpty()) {
            return Optional.empty();
        }

        var account = accounts.getQualifying();
        if (account.isEmpty()) {
            return Optional.empty();
        }

        var op =
                scheduleSign(schedulesQualifying.get())
                        .logged()
                        .alsoSigningWith(STABLE_RECEIVER)
                        .hasAnyPrecheck()
                        .hasKnownStatusFrom(permissibleOutcomes);
        return Optional.of(op);
    }
}
