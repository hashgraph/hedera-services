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
import com.hederahashgraph.api.proto.java.Query;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public interface QueryMutation extends BiFunction<Query, HapiSpec, Query> {
    /**
     * Constructs a {@link HapiSpec}-agnostic {@link QueryMutation} that simply applies
     * the given transform to the {@link Query}.
     *
     * @param operator the transform to apply to the query
     * @return the mutation for the transform
     */
    static QueryMutation withTransform(@NonNull final UnaryOperator<Query> operator) {
        return (query, spec) -> operator.apply(query);
    }
}
