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

import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import edu.umd.cs.findbugs.annotations.NonNull;

/** A {@code QueryHandler} contains all methods for the different stages of a single query. */
public interface QueryHandler {

    /**
     * Extract the {@link QueryHeader} from a given {@link Query}
     *
     * @param query the {@link Query} that contains the header
     * @return the {@link QueryHeader} that was specified in the query
     * @throws NullPointerException if {@code query} is {@code null}
     */
    QueryHeader extractHeader(@NonNull Query query);

    /**
     * Creates an empty {@link Response} with a provided header. This is typically used, if an error
     * occurred. The {@code header} contains a {@link
     * com.hederahashgraph.api.proto.java.ResponseCodeEnum} with the error code.
     *
     * @param header the {@link ResponseHeader} that needs to be included
     * @return the created {@link Response}
     * @throws NullPointerException if {@code header} is {@code null}
     */
    Response createEmptyResponse(@NonNull ResponseHeader header);

    /**
     * Returns {@code true}, if the query associated with this handler is only requesting the cost
     * of this query alone (i.e. the query won't be executed)
     *
     * @param responseType the {@link ResponseType} of a query, because payment can depend on the
     *     response type
     * @return {@code true} if payment is required, {@code false} otherwise
     * @throws NullPointerException if {@code responseType} is {@code null}
     */
    boolean requiresNodePayment(@NonNull final ResponseType responseType);

    /**
     * Returns {@code true}, if a query associated with this handler returns only the costs
     *
     * @param responseType the {@link ResponseType} of a query, because the result can depend on the
     *     response type
     * @return {@code true} if only costs need to returned, {@code false} otherwise
     * @throws NullPointerException if {@code responseType} is {@code null}
     */
    boolean needsAnswerOnlyCost(@NonNull final ResponseType responseType);
}
