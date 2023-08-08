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

package com.hedera.node.app.service.contract.impl.exec;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public record CallOutcome(@Nullable ContractFunctionResult result, @NonNull ResponseCodeEnum status) {
    public boolean isSuccess() {
        return status == SUCCESS;
    }

    public @Nullable ContractID recipientIdIfCalled() {
        return result == null ? null : result.contractIDOrThrow();
    }

    public @Nullable ContractID recipientIdIfCreated() {
        return representsTopLevelCreation() ? requireNonNull(result).contractIDOrThrow() : null;
    }

    private boolean representsTopLevelCreation() {
        return isSuccess() && requireNonNull(result).hasEvmAddress();
    }
}
