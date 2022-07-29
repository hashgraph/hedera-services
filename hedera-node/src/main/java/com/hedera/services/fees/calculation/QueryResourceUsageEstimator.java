/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.calculation;

import com.hedera.services.context.primitives.StateView;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Defines a type able to estimate the resource usage of one (or more) query operations, relative to
 * a particular state of the world.
 */
public interface QueryResourceUsageEstimator {
    /**
     * Flags whether the estimator applies to the given query.
     *
     * @param query the query in question
     * @return if the estimator applies
     */
    boolean applicableTo(final Query query);

    /**
     * Returns the estimated resource usage for the given query relative to the given state of the
     * world.
     *
     * @param query the query in question
     * @param view the state of the world
     * @return the estimated resource usage
     * @throws NullPointerException or analogous if the estimator does not apply to the query
     */
    default FeeData usageGiven(final Query query, final StateView view) {
        return usageGiven(query, view, null);
    }

    /**
     * Returns the estimated resource usage for the given query relative to the given state of the
     * world, with a context for storing any information that may be useful for by later stages of
     * the query answer flow.
     *
     * @param query the query in question
     * @param view the state of the world
     * @param queryCtx the context of the query being answered
     * @return the estimated resource usage
     * @throws NullPointerException or analogous if the estimator does not apply to the query
     */
    FeeData usageGiven(
            final Query query, final StateView view, @Nullable final Map<String, Object> queryCtx);

    /**
     * Returns the estimated resource usage for the given query relative to the given state of the
     * world and response type.
     *
     * @param query the query in question
     * @param view the state of the world
     * @param type the response type of the given query
     * @return the estimated resource usage
     * @throws NullPointerException or analogous if the estimator does not apply to the query
     */
    default FeeData usageGivenType(
            final Query query, final StateView view, final ResponseType type) {
        return usageGiven(query, view);
    }
}
