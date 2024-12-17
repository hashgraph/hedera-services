/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hederahashgraph.api.proto.java.FeeData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;

/**
 * Used to help calculate the fees for a given transaction. The calculator is available on the {@link HandleContext},
 * and is already configured with common information about the transaction, such as the number of bytes in the
 * signature map, and the number of signature verifications than have been performed, and the number of bytes in the
 * transaction memo, etc. The handler can use the calculator to determine the {@link Fees} for the transaction. It can
 * then also apply any additional fees that are required by the handler.
 */
public interface FeeCalculator {
    /**
     * Used for applying usage-based congestion pricing, by reporting the percentage of the resource that is being
     * used. A standard algorithm is used to scale the fees accordingly.
     *
     * @param percent The percentage used. Must be a value between 0 and 1. Any other value will be clamped to that
     *                range.
     * @return {@code this} for fluent usage.
     */
    @NonNull
    FeeCalculator withResourceUsagePercent(double percent);

    /**
     * Adds to the "bpt" component the number of bytes used by the transaction.
     * @param bytes The number of bytes. Must not be negative.
     * @return {@code this} for fluent usage.
     */
    @NonNull
    FeeCalculator addBytesPerTransaction(long bytes);

    /**
     * Adds to the "sbs" component the number of storage seconds used by the transaction.
     * @param seconds The number of seconds. Must not be negative.
     * @return {@code this} for fluent usage.
     */
    @NonNull
    FeeCalculator addStorageBytesSeconds(long seconds);

    /**
     * Adds to the "networkRbs" component the number of network ram byte seconds used by the transaction.
     * @param amount The number of bytes for given lifetime. Must not be negative.
     * @return {@code this} for fluent usage.
     */
    @NonNull
    FeeCalculator addNetworkRamByteSeconds(long amount);

    /**
     * Adds to the "rbs" component the number of ram byte seconds used by the transaction.
     * @param amount The number of bytes for given lifetime. Must not be negative.
     * @return {@code this} for fluent usage.
     */
    @NonNull
    FeeCalculator addRamByteSeconds(long amount);

    /**
     * Adds to the "vpt" component the number of verifications used by the transaction.
     * @param amount The number of verifications per transaction. Must not be negative.
     * @return {@code this} for fluent usage.
     */
    @NonNull
    FeeCalculator addVerificationsPerTransaction(long amount);

    @NonNull
    Fees legacyCalculate(@NonNull final Function<SigValueObj, FeeData> callback);

    /**
     * Calculates and returns the {@link Fees} for the transaction.
     * @return The fees.
     */
    @NonNull
    Fees calculate();

    long getCongestionMultiplier();

    /**
     * Resets the usage of all components to zero.
     * @return
     */
    @NonNull
    FeeCalculator resetUsage();
}
