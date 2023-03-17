/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class holds static factory methods to create suppliers of {@link TransactionRequestConfig}.
 */
public final class TransactionRequestSupplierFactory {

    /**
     * This method decides which instance of {@code Supplier<TransactionRequestConfig>} must be
     * used, based on the given {@code virtualMerkleConfig}.
     *
     * @param virtualMerkleConfig
     * 		The configurations to create the requests of transactions.
     * @return A supplier of {@link TransactionRequestConfig}.
     */
    public static final Supplier<TransactionRequestConfig> create(final VirtualMerkleConfig virtualMerkleConfig) {
        final List<TransactionRequestConfig> requestConfigs = virtualMerkleConfig.getSequential().stream()
                .filter(config -> config.getAmount() > 0)
                .map(TransactionRequestConfig::new)
                .collect(Collectors.toList());
        if (virtualMerkleConfig.isAssorted()) {
            return new AssortedTransactionRequestSupplier(requestConfigs);
        }
        return new SequentialTransactionRequestSupplier(requestConfigs);
    }
}
