/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.api.implementation.workflows.prehandle.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.services.api.implementation.ServicesAccessor;
import com.hedera.services.api.implementation.workflows.prehandle.PreHandleDispatcher;
import com.hederahashgraph.api.proto.java.TransactionBody;

/** Default implementation of {@link PreHandleDispatcher} */
public final class PreHandleDispatcherImpl implements PreHandleDispatcher {
    private final ServicesAccessor services;

    /**
     * Constructor of {@code PreHandleDispatcherImpl}
     *
     * @param services the {@link ServicesAccessor} with all available services
     * @throws NullPointerException if {@code services} is {@code null}
     */
    public PreHandleDispatcherImpl(final ServicesAccessor services) {
        this.services = requireNonNull(services);
    }

    @Override
    public void dispatch(final TransactionBody transactionBody) {
        requireNonNull(transactionBody);
        switch (transactionBody.getDataCase()) {
                //            case FILE_CREATE ->
                // services.fileService().preHandler().preHandleFileCreate(transactionBodyData.as());
                //            case CRYPTO_CREATE_ACCOUNT ->
                //
                // services.cryptoService().preHandler().preHandleAccountCreate(transactionBodyData.as());
            default -> throw new IllegalArgumentException(
                    "Unexpected kind " + transactionBody.getDataCase());
        }
    }
}
