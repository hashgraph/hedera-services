/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A strategy for validating and charging fees for a transaction.
 */
public interface FeeCharging {
    interface Validation {
        boolean creatorDidDueDiligence();

        @Nullable
        ResponseCodeEnum maybeErrorStatus();
    }

    Validation validate(
            @NonNull Account payer,
            @NonNull AccountID creatorId,
            @NonNull Fees fees,
            @NonNull TransactionBody body,
            boolean isDuplicate,
            @NonNull HederaFunctionality function,
            @Deprecated @NonNull HandleContext.TransactionCategory category);
}
