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

package com.hedera.node.app.spi.api;

import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Factory for creating stores and service APIs.
 */
public interface ServiceApiFactory {

    /**
     * Return a service API given the API's interface. This permits use of another service
     * that doesn't have a corresponding HAPI {@link TransactionBody}.
     *
     * @param apiInterface The API interface to find and create an implementation of
     * @param <T> Interface class for an API
     * @return An implementation of the provided API interface
     * @throws IllegalArgumentException if the apiInterface class provided is unknown to the app
     * @throws NullPointerException if {@code apiInterface} is {@code null}
     */
    @NonNull
    <T> T serviceApi(@NonNull Class<T> apiInterface);
}