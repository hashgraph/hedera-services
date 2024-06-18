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

package com.hedera.node.app.spi.fees;

import com.hedera.hapi.node.base.SubType;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A factory for creating {@link FeeCalculator} instances.
 */
public interface FeeCalculatorFactory {

    /**
     * Get a calculator for calculating fees for the current transaction, and its {@link SubType}. Most transactions
     * just use {@link SubType#DEFAULT}, but some (such as crypto transfer) need to be more specific.
     *
     * @param subType The {@link SubType} of the transaction.
     * @return The {@link FeeCalculator} to use.
     */
    @NonNull
    FeeCalculator feeCalculator(@NonNull final SubType subType);
}
