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

package com.hedera.node.app.fees;

import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.Fees;
import com.hederahashgraph.api.proto.java.FeeData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;

/**
 * A no-op implementation of {@link FeeCalculator} that always returns {@link Fees#FREE}.
 */
public class NoOpFeeCalculator implements FeeCalculator {

    public static final NoOpFeeCalculator INSTANCE = new NoOpFeeCalculator();

    @NonNull
    @Override
    public FeeCalculator withResourceUsagePercent(double percent) {
        return this;
    }

    @NonNull
    @Override
    public FeeCalculator addBytesPerTransaction(long bytes) {
        return this;
    }

    @NonNull
    @Override
    public FeeCalculator addStorageBytesSeconds(long seconds) {
        return this;
    }

    @NonNull
    @Override
    public FeeCalculator addNetworkRamByteSeconds(long amount) {
        return this;
    }

    @NonNull
    @Override
    public FeeCalculator addRamByteSeconds(long amount) {
        return this;
    }

    @NonNull
    public FeeCalculator addVerificationsPerTransaction(long amount) {
        return this;
    }

    @NonNull
    public FeeCalculator resetUsage() {
        return this;
    }

    @NonNull
    @Override
    public Fees legacyCalculate(@NonNull Function<SigValueObj, FeeData> callback) {
        return Fees.FREE;
    }

    @NonNull
    @Override
    public Fees calculate() {
        return Fees.FREE;
    }

    @Override
    public long getCongestionMultiplier() {
        return 1;
    }
}
