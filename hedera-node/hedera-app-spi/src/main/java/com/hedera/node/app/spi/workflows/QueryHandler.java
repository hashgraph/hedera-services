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
package com.hedera.node.app.spi.workflows;

import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_STATE_PROOF;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import edu.umd.cs.findbugs.annotations.NonNull;

/** A {@code QueryHandler} contains all methods for the different stages of a single query. */
public interface QueryHandler {

    QueryHeader extractHeader(@NonNull Query query);

    Response createEmptyResponse(@NonNull ResponseHeader header);

    default boolean requiresNodePayment(@NonNull final ResponseType responseType) {
        return responseType == ANSWER_ONLY || responseType == ANSWER_STATE_PROOF;
    }

    default boolean needsAnswerOnlyCost(@NonNull final ResponseType responseType) {
        return COST_ANSWER == responseType;
    }
}
