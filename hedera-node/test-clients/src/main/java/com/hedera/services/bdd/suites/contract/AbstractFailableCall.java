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

package com.hedera.services.bdd.suites.contract;

import com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode;
import com.hedera.services.bdd.suites.contract.classiccalls.FailableClassicCall;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class AbstractFailableCall implements FailableClassicCall {
    private final Set<ClassicFailureMode> failureModes;

    private Map<ClassicFailureMode, List<TransactionRecord>> modeRecords = new EnumMap<>(ClassicFailureMode.class);
    private Map<ClassicFailureMode, ContractFunctionResult> modeResults = new EnumMap<>(ClassicFailureMode.class);

    protected AbstractFailableCall(@NonNull final Set<ClassicFailureMode> failureModes) {
        this.failureModes = Objects.requireNonNull(failureModes);
    }

    @Override
    public boolean hasFailureMode(@NonNull ClassicFailureMode mode) {
        return failureModes.contains(mode);
    }

    @Override
    public void reportOnAssertedFailureModes() {
        if (staticCallOk()) {
            System.out.println("---- STATIC " + name() + " ----");
            modeResults.forEach((mode, result) -> System.out.println(mode.name() + " ➡️ " + result));
            System.out.println("----------------\n");
        }
        System.out.println("---- CALL " + name() + " ----");
        modeRecords.forEach((mode, records) -> System.out.println(mode.name() + " ➡️ " + records));
        System.out.println("----------------\n");
    }

    protected void rememberRecordsForMode(
            @NonNull final ClassicFailureMode mode, @NonNull final List<TransactionRecord> records) {
        modeRecords.put(mode, records);
    }

    protected void rememberResultForStaticMode(
            @NonNull final ClassicFailureMode mode, @NonNull final ContractFunctionResult result) {
        modeResults.put(mode, result);
    }

    protected void throwIfUnsupported(@NonNull final ClassicFailureMode mode) {
        if (!hasFailureMode(mode)) {
            throw new IllegalArgumentException("Unsupported failure mode " + mode + " for " + name());
        }
    }
}
