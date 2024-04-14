/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.mod;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class SubmitModificationsOp extends UtilOp {
    private final Supplier<HapiTxnOp<?>> txnOpSupplier;
    private final Function<Transaction, List<Modification>> modificationsFn;

    public SubmitModificationsOp(
            @NonNull final Supplier<HapiTxnOp<?>> txnOpSupplier,
            @NonNull final Function<Transaction, List<Modification>> modificationsFn) {
        this.txnOpSupplier = txnOpSupplier;
        this.modificationsFn = modificationsFn;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        final List<Modification> modifications = new ArrayList<>();
        allRunFor(
                spec,
                txnOpSupplier.get().withTxnTransform(txn -> {
                    modifications.addAll(modificationsFn.apply(txn));
                    return txn;
                }),
                sourcing(() -> blockingOrder(modifications.stream()
                        .flatMap(modification -> {
                            final HapiTxnOp<?> op = txnOpSupplier.get();
                            modification.customize(op);
                            return Stream.of(logIt(modification.summary()), op);
                        })
                        .toArray(HapiSpecOperation[]::new))));
        return false;
    }
}
