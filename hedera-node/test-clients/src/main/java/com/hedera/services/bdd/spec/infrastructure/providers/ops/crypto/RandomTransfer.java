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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto;

import static com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccount.INITIAL_BALANCE;
import static com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccount.SEND_THRESHOLD;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.LookupUtils;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RandomTransfer implements OpProvider {
    private static final Logger log = LogManager.getLogger(RandomTransfer.class);

    public static final int DEFAULT_NUM_STABLE_ACCOUNTS = 200;
    public static final double DEFAULT_RECORD_PROBABILITY = 0.0;

    private final ResponseCodeEnum[] permissibleOutcomes =
            standardOutcomesAnd(ACCOUNT_DELETED, INSUFFICIENT_ACCOUNT_BALANCE);

    private int numStableAccounts = DEFAULT_NUM_STABLE_ACCOUNTS;
    public double recordProb = DEFAULT_RECORD_PROBABILITY;
    private final SplittableRandom r = new SplittableRandom();
    private final EntityNameProvider<AccountID> accounts;

    public RandomTransfer(EntityNameProvider<AccountID> accounts) {
        this.accounts = accounts;
    }

    public RandomTransfer numStableAccounts(int n) {
        numStableAccounts = n;
        return this;
    }

    public RandomTransfer recordProbability(double p) {
        recordProb = p;
        return this;
    }

    private String my(String opName) {
        return unique(opName, RandomTransfer.class);
    }

    public static Set<String> stableAccounts(int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> String.format("stable-account%d", i))
                .collect(toSet());
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        return stableAccounts(numStableAccounts).stream()
                .map(account -> cryptoCreate(my(account))
                        .noLogging()
                        .balance(INITIAL_BALANCE)
                        .deferStatusResolution()
                        .payingWith(UNIQUE_PAYER_ACCOUNT)
                        .rechargeWindow(3))
                .collect(toList());
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        final var involved = LookupUtils.twoDistinct(accounts);
        if (involved.isEmpty()) {
            return Optional.empty();
        }

        boolean shouldCreateRecord = r.nextDouble() < recordProb;
        long amount = shouldCreateRecord ? (SEND_THRESHOLD + 1) : 1;
        String from = involved.get().getKey(), to = involved.get().getValue();

        HapiCryptoTransfer op = cryptoTransfer(tinyBarsFromTo(from, to, amount))
                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                .hasKnownStatusFrom(permissibleOutcomes)
                .payingWith(UNIQUE_PAYER_ACCOUNT);

        return Optional.of(op);
    }
}
