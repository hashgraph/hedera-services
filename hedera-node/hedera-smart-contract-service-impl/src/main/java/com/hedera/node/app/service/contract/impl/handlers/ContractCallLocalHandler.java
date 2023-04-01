/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.handlers;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.contract.ContractCallLocalResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import static java.util.Objects.requireNonNull;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONTRACT_CALL_LOCAL}.
 */
@Singleton
public class ContractCallLocalHandler extends PaidQueryHandler {
    @Inject
    public ContractCallLocalHandler() {
        // Exists for injection
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.contractCallLocalOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        final var response = ContractCallLocalResponse.newBuilder().header(header);
        return Response.newBuilder().contractCallLocal(response).build();
    }

    /**
     * This method is called during the query workflow. It validates the query, but does not
     * determine the response yet.
     *
     * @param query the {@link Query} that should be validated
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if validation fails
     */
    public ResponseCodeEnum validate(@NonNull final Query query) throws PreCheckException {
        Objects.requireNonNull(query);
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * This method is called during the query workflow. It determines the requested value(s) and
     * returns the appropriate response.
     *
     * @param query the {@link Query} with the request
     * @param header the {@link ResponseHeader} that should be used, if the request was successful
     * @return a {@link Response} with the requested values
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public Response findResponse(@NonNull final Query query, @NonNull final ResponseHeader header) {
        Objects.requireNonNull(query);
        Objects.requireNonNull(header);
        throw new UnsupportedOperationException("Not implemented");
    }
}
