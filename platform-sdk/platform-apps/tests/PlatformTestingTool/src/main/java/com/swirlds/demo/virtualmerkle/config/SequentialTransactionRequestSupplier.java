/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.virtualmerkle.config;

import com.swirlds.logging.legacy.LogMarker;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The responsibility of this class is to supply {@link TransactionRequestConfig} instances in
 * a sequential order.
 */
public final class SequentialTransactionRequestSupplier implements Supplier<TransactionRequestConfig> {

    private static final Logger logger = LogManager.getLogger(SequentialTransactionRequestSupplier.class);

    private final List<TransactionRequestConfig> sequenceOfConfigs;
    private int transactionConfigIndex = 0;

    public SequentialTransactionRequestSupplier(final List<TransactionRequestConfig> requestConfigs) {
        this.sequenceOfConfigs = requestConfigs;
    }

    @Override
    public TransactionRequestConfig get() {
        if (transactionConfigIndex == sequenceOfConfigs.size()) {
            return null;
        }

        final TransactionRequestConfig transactionRequestConfig = sequenceOfConfigs.get(transactionConfigIndex);
        if (transactionRequestConfig.decrementAndGetAmount() == 0) {
            this.transactionConfigIndex++;
            logger.info(
                    LogMarker.DEMO_INFO.getMarker(),
                    "Finished generating {} transactions",
                    transactionRequestConfig.getType());
        }

        return transactionRequestConfig;
    }
}
