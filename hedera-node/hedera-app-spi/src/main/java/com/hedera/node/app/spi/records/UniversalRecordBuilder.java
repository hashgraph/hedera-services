/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.records;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Base implementation of a {@code RecordBuilder} that will (eventually) track all the
 * "universal" transaction metadata and side-effects.
 *
 * <p>Serves as implementation support for all transaction-specific record builders.
 */
public abstract class UniversalRecordBuilder<T extends RecordBuilder<T>> implements RecordBuilder<T> {
    private ResponseCodeEnum status = null;

    protected abstract T self();

    /**
     * {@inheritDoc}
     */
    @Override
    public T setFinalStatus(@NonNull final ResponseCodeEnum status) {
        this.status = Objects.requireNonNull(status);
        return self();
    }

    @Override
    @NonNull
    public ResponseCodeEnum getFinalStatus() {
        throwIfMissingData();
        return Objects.requireNonNull(status);
    }

    private void throwIfMissingData() {
        if (status == null) {
            throw new IllegalStateException("No final status was recorded");
        }
    }
}
