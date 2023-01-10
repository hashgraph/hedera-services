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
package com.hedera.services.bdd.spec.infrastructure.providers.ops;

import static java.util.Collections.binarySearch;
import static java.util.stream.Collectors.toList;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BiasedDelegatingProvider implements OpProvider {
    private static final Logger log = LogManager.getLogger(BiasedDelegatingProvider.class);

    private final Random r = new Random();
    private final List<Integer> cumulativeBias = new ArrayList<>(List.of(0));
    private final List<OpProvider> delegates = new ArrayList<>();
    private final List<HapiSpecOperation> globalInitializers = new ArrayList<>();
    private final List<Supplier<HapiSpecOperation[]>> summaries = new ArrayList<>();

    private boolean shouldAlwaysDefer = true;
    private boolean shouldLogNormalFlow = false;

    public BiasedDelegatingProvider withOp(OpProvider delegate, int bias) {
        if (bias != 0) {
            int n = delegates.size();
            cumulativeBias.add(cumulativeBias.get(n) + bias);
            delegates.add(delegate);
        }
        return this;
    }

    public BiasedDelegatingProvider withInitialization(HapiSpecOperation... ops) {
        globalInitializers.addAll(List.of(ops));
        return this;
    }

    public BiasedDelegatingProvider withSummary(Supplier<HapiSpecOperation[]> summary) {
        summaries.add(summary);
        return this;
    }

    public BiasedDelegatingProvider shouldAlwaysDefer(boolean flag) {
        shouldAlwaysDefer = flag;
        return this;
    }

    public BiasedDelegatingProvider shouldLogNormalFlow(boolean flag) {
        shouldLogNormalFlow = flag;
        return this;
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        return Stream.concat(
                        globalInitializers.stream(),
                        delegates.stream().flatMap(d -> d.suggestedInitializers().stream()))
                .collect(toList());
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        if (delegates.isEmpty()) {
            return Optional.empty();
        } else {
            while (true) {
                Optional<HapiSpecOperation> op = delegates.get(randomSelection()).get();
                if (op.isPresent()) {
                    op.ifPresent(this::configureDefaults);
                    return op;
                }
            }
        }
    }

    private int randomSelection() {
        int bias = r.nextInt(cumulativeBias.get(cumulativeBias.size() - 1));
        int selection = binarySearch(cumulativeBias, bias);
        if (selection < 0) {
            selection = -1 * selection - 2;
        }
        return selection;
    }

    private void configureDefaults(HapiSpecOperation op) {
        boolean isTxnOp = isTxnOp(op);

        if (shouldAlwaysDefer && isTxnOp) {
            ((HapiTxnOp) op).deferStatusResolution();
        }
        if (!shouldLogNormalFlow) {
            if (isTxnOp) {
                ((HapiTxnOp) op).noLogging().payingWith(UNIQUE_PAYER_ACCOUNT).fee(TRANSACTION_FEE);
            } else if (isQueryOp(op)) {
                ((HapiQueryOp) op).noLogging().payingWith(UNIQUE_PAYER_ACCOUNT);
            }
        }
    }

    boolean isTxnOp(HapiSpecOperation op) {
        return (op instanceof HapiTxnOp);
    }

    boolean isQueryOp(HapiSpecOperation op) {
        return (op instanceof HapiQueryOp);
    }
}
