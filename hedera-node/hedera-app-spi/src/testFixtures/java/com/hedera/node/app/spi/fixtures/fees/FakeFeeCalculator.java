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

package com.hedera.node.app.spi.fixtures.fees;

import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.Fees;
import com.hederahashgraph.api.proto.java.FeeData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;

public class FakeFeeCalculator implements FeeCalculator {
    @Override
    @NonNull
    public FeeCalculator withResourceUsagePercent(double percent) {
        return this;
    }

    @Override
    @NonNull
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
    public FeeCalculator addRamByteSeconds(long amount) {
        return this;
    }

    @NonNull
    public FeeCalculator addVerificationsPerTransaction(long amount) {
        return this;
    }

    @Override
    @NonNull
    public Fees calculate() {
        return new Fees(0, 0, 0);
    }

    @Override
    public long getCongestionMultiplier() {
        return 1;
    }

    @NonNull
    @Override
    public Fees legacyCalculate(@NonNull Function<SigValueObj, FeeData> callback) {
        callback.apply(new SigValueObj(0, 0, 0));
        return new Fees(0, 0, 0);
    }

    @NonNull
    public FakeFeeCalculator resetUsage() {
        return this;
    }

    @Override
    public long getVptPrice() {
        return 0;
    }
}
