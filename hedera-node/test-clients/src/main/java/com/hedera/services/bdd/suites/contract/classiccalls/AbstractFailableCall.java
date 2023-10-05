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

package com.hedera.services.bdd.suites.contract.classiccalls;

import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class AbstractFailableCall implements FailableClassicCall {
    private final Set<ClassicFailureMode> failureModes;

    protected AbstractFailableCall(@NonNull final Set<ClassicFailureMode> failureModes) {
        this.failureModes = Objects.requireNonNull(failureModes);
    }

    @Override
    public boolean hasFailureMode(@NonNull ClassicFailureMode mode) {
        return failureModes.contains(mode);
    }

    @Override
    public FailableCallResult asCallResult(List<TransactionRecord> records) {
        final var topLevelRecord = records.get(0);
        if (records.size() > 1) {
            final var childRecord = records.get(1);
            return new FailableCallResult(
                    topLevelRecord.getReceipt().getStatus(),
                    topLevelRecord.getContractCallResult().getErrorMessage(),
                    childRecord.getReceipt().getStatus(),
                    childRecord.getContractCallResult().getErrorMessage());
        } else {
            return new FailableCallResult(
                    topLevelRecord.getReceipt().getStatus(),
                    topLevelRecord.getContractCallResult().getErrorMessage(),
                    null,
                    null);
        }
    }

    @Override
    public FailableStaticCallResult asStaticCallResult(ResponseCodeEnum topLevelStatus, ContractFunctionResult result) {
        return new FailableStaticCallResult(topLevelStatus, result.getErrorMessage());
    }

    protected void throwIfUnsupported(@NonNull final ClassicFailureMode mode) {
        if (!hasFailureMode(mode)) {
            throw new IllegalArgumentException("Unsupported failure mode " + mode + " for " + name());
        }
    }
}
