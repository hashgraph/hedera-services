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

package com.swirlds.base.sample.service;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;

/**
 * A service that supports crud operations for {@code <T>}
 * @param <T>
 */
public class CrudService<T> {

    private final Class<T> resultType;

    public CrudService(@NonNull final Class<T> resultType) {
        this.resultType = resultType;
    }

    public void delete(final @NonNull String key) {
        throw new UnsupportedOperationException("Delete Operation not supported");
    }

    public @NonNull T update(final @NonNull String key, final @NonNull T body) {
        throw new UnsupportedOperationException("Update Operation not supported");
    }

    public @NonNull T create(final @NonNull T body) {
        throw new UnsupportedOperationException("Create Operation not supported");
    }

    public @Nullable T retrieve(final @NonNull String key) {
        throw new UnsupportedOperationException("Retrieve Operation not supported");
    }

    public @NonNull List<T> retrieveAll(@NonNull Map<String, String> params) {
        throw new UnsupportedOperationException("Retrieve All Operation not supported");
    }

    public @NonNull Class<T> getResultType() {
        return this.resultType;
    }
}
