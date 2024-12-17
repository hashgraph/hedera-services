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

import com.hedera.services.bdd.spec.HapiSpec;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * Defines a type able to mutate a {@link TransactionBody.Builder} in the
 * context of a {@link HapiSpec}.
 */
public interface BodyMutation extends BiFunction<TransactionBody.Builder, HapiSpec, TransactionBody.Builder> {
    /**
     * Constructs a {@link HapiSpec}-agnostic {@link BodyMutation} that simply applies
     * the given transform to the {@link TransactionBody}.
     *
     * @param operator the transform to apply to the body
     * @return the mutation for the transform
     */
    static BodyMutation withTransform(@NonNull final UnaryOperator<TransactionBody> operator) {
        return (builder, spec) -> operator.apply(builder.build()).toBuilder();
    }
}
