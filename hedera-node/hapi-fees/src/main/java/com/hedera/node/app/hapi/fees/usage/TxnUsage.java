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
package com.hedera.node.app.hapi.fees.usage;

import static com.hedera.node.app.hapi.fees.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.getAccountKeyStorageSize;

import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class TxnUsage {
    protected static final int AMOUNT_REPR_BYTES = 8;
    protected static final UsageProperties usageProperties = USAGE_PROPERTIES;

    protected final TransactionBody op;
    protected final TxnUsageEstimator usageEstimator;

    protected TxnUsage(final TransactionBody op, final TxnUsageEstimator usageEstimator) {
        this.op = op;
        this.usageEstimator = usageEstimator;
    }

    public static <T> long keySizeIfPresent(
            final T op, final Predicate<T> check, final Function<T, Key> getter) {
        return check.test(op) ? getAccountKeyStorageSize(getter.apply(op)) : 0L;
    }

    protected void addAmountBpt() {
        usageEstimator.addBpt(AMOUNT_REPR_BYTES);
    }

    protected void addEntityBpt() {
        usageEstimator.addBpt(BASIC_ENTITY_ID_SIZE);
    }

    protected void addNetworkRecordRb(final long rb) {
        usageEstimator.addNetworkRbs(rb * usageProperties.legacyReceiptStorageSecs());
    }

    protected void addRecordRb(final long rb) {
        usageEstimator.addRbs(rb * usageProperties.legacyReceiptStorageSecs());
    }
}
