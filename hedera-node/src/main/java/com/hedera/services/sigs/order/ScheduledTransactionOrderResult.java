package com.hedera.services.sigs.order;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.swirlds.common.CommonUtils;

import java.util.Optional;

public class ScheduledTransactionOrderResult<T> {
    private final byte[] transactionBody;
    private final Optional<T> errorReport;

    private static final ScheduledTransactionOrderResult<?> NO_KNOWN_TRANSACTION_BODY = new ScheduledTransactionOrderResult<>(null);

    @SuppressWarnings("unchecked")
    public static <T> ScheduledTransactionOrderResult<T> noTransactionBody() {
        return (ScheduledTransactionOrderResult<T>)NO_KNOWN_TRANSACTION_BODY;
    }

    public ScheduledTransactionOrderResult(byte[] transactionBody) {
        this(transactionBody, Optional.empty());
    }
    public ScheduledTransactionOrderResult(T errorReport) {
        this(null, Optional.of(errorReport));
    }
    public ScheduledTransactionOrderResult(byte[] transactionBody, Optional<T> errorReport) {
        this.transactionBody = transactionBody;
        this.errorReport = errorReport;
    }

    public boolean hasKnownOrder() {
        return !errorReport.isPresent();
    }

    public boolean hasErrorReport() {
        return errorReport.isPresent();
    }

    public byte[] getTransactionBody() {
        return transactionBody;
    }

    public T getErrorReport() {
        return errorReport.get();
    }

    @Override
    public String toString() {
        if (hasErrorReport()) {
            return MoreObjects.toStringHelper(ScheduledTransactionOrderResult.class)
                    .add("outcome", "FAILURE")
                    .add("details", errorReport.get())
                    .toString();
        } else {
            return MoreObjects.toStringHelper(ScheduledTransactionOrderResult.class)
                    .add("outcome", "SUCCESS")
                    .add("transactionBody", CommonUtils.hex(transactionBody))
                    .toString();
        }
    }
}
