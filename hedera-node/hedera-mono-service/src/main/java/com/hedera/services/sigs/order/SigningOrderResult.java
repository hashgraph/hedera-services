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
package com.hedera.services.sigs.order;

import static java.util.Collections.EMPTY_LIST;

import com.google.common.base.MoreObjects;
import com.hedera.services.legacy.core.jproto.JKey;
import java.util.List;

/**
 * Summarize the outcome of trying to list, in canonical order, the Hedera keys that must have
 * active signatures for some gRCP transaction to be valid.
 *
 * <p>The main purpose of this class is to let calls to methods of {@link SigRequirements} be
 * unchecked. This makes it easier to read and understand client code.
 *
 * @param <T> the type of error report that may be contained in this summary.
 */
public class SigningOrderResult<T> {
    private final List<JKey> orderedKeys;
    private final T errorReport;

    private static final SigningOrderResult<?> NO_KNOWN_KEYS = new SigningOrderResult<>(EMPTY_LIST);

    @SuppressWarnings("unchecked")
    public static <T> SigningOrderResult<T> noKnownKeys() {
        return (SigningOrderResult<T>) NO_KNOWN_KEYS;
    }

    public SigningOrderResult(List<JKey> orderedKeys) {
        this(orderedKeys, null);
    }

    public SigningOrderResult(T errorReport) {
        this(EMPTY_LIST, errorReport);
    }

    private SigningOrderResult(List<JKey> orderedKeys, T errorReport) {
        this.orderedKeys = orderedKeys;
        this.errorReport = errorReport;
    }

    public boolean hasErrorReport() {
        return errorReport != null;
    }

    public List<JKey> getOrderedKeys() {
        return orderedKeys;
    }

    public JKey getPayerKey() {
        return orderedKeys.get(0);
    }

    public T getErrorReport() {
        return errorReport;
    }

    @Override
    public String toString() {
        if (hasErrorReport()) {
            return MoreObjects.toStringHelper(SigningOrderResult.class)
                    .add("outcome", "FAILURE")
                    .add("details", errorReport)
                    .toString();
        } else {
            return MoreObjects.toStringHelper(SigningOrderResult.class)
                    .add("outcome", "SUCCESS")
                    .add("keys", orderedKeys)
                    .toString();
        }
    }
}
