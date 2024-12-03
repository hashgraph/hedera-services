/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hederahashgraph.api.proto.java.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public abstract class UtilOp extends HapiSpecOperation {
    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerSigs) throws Throwable {
        return 0;
    }

    public UtilOp logged() {
        verboseLoggingOn = true;
        return this;
    }

    /**
     * Given an array of mixed
     * <ul>
     *     <li>{@link SpecOperation},</li>
     *     <li>{@link SpecOperation}[],</li>
     *     <li>{@link Collection}&lt;{@link SpecOperation}></li>
     * </ul>
     * flatten it into an {@link SpecOperation}[] with all the elements in the order iterated. (Only
     * flattens top-level.)
     */
    @SuppressWarnings("rawtypes")
    public static @NonNull SpecOperation[] flatten(@NonNull final Object... sos) {
        final var ops = new ArrayList<SpecOperation>();
        for (final var o : sos) {
            switch (o) {
                case SpecOperation so -> ops.add(so);
                case SpecOperation[] aso -> Collections.addAll(ops, aso);
                case Collection co -> {
                    for (final var e : co) {
                        if (e instanceof SpecOperation so) {
                            ops.add(so);
                        }
                        // and ... silently ignore anything in the collection that's _not_ a `SpecOperation` ...
                    }
                }
                default -> {
                    /* silently ignore anything in the top-level array that's _not_ what we expect ... */
                }
            }
        }
        return ops.toArray(new SpecOperation[0]);
    }
}
