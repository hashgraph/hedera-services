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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.schedule;

import static com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccount.INITIAL_BALANCE;
import static com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomTransfer.stableAccounts;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
import static java.util.stream.Collectors.toList;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.LookupUtils;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class RandomSchedule implements OpProvider {
    private final AtomicInteger opNo = new AtomicInteger();
    private final RegistrySourcedNameProvider<ScheduleID> schedules;
    private final EntityNameProvider<AccountID> accounts;
    public final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(UNRESOLVABLE_REQUIRED_SIGNERS);

    private final ResponseCodeEnum[] outcomesForTransfer =
            standardOutcomesAnd(ACCOUNT_DELETED, INSUFFICIENT_ACCOUNT_BALANCE);

    public static final int DEFAULT_CEILING_NUM = 100;
    private int ceilingNum = DEFAULT_CEILING_NUM;
    static final String ADMIN_KEY = DEFAULT_PAYER;
    static final String STABLE_RECEIVER = "stable-receiver";

    public RandomSchedule(RegistrySourcedNameProvider<ScheduleID> schedules, EntityNameProvider<AccountID> accounts) {
        this.schedules = schedules;
        this.accounts = accounts;
    }

    public RandomSchedule ceiling(int n) {
        ceilingNum = n;
        return this;
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        return stableAccounts(1).stream()
                .map(account -> cryptoCreate(STABLE_RECEIVER)
                        .noLogging()
                        .balance(INITIAL_BALANCE)
                        .deferStatusResolution()
                        .payingWith(UNIQUE_PAYER_ACCOUNT)
                        .receiverSigRequired(true))
                .collect(toList());
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        if (schedules.numPresent() >= ceilingNum) {
            return Optional.empty();
        }
        final var involved = LookupUtils.twoDistinct(accounts);
        if (involved.isEmpty()) {
            return Optional.empty();
        }
        int id = opNo.getAndIncrement();

        String from = involved.get().getKey(), to = involved.get().getValue();

        HapiScheduleCreate op = scheduleCreate(
                        "schedule" + id,
                        cryptoTransfer(tinyBarsFromTo(from, STABLE_RECEIVER, 1))
                                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS))
                .signedBy(DEFAULT_PAYER)
                .fee(ONE_HUNDRED_HBARS)
                .alsoSigningWith(from)
                .memo("randomlycreated" + id)
                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                .hasKnownStatusFrom(permissibleOutcomes)
                .adminKey(ADMIN_KEY);
        return Optional.of(op);
    }
}
