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

package com.hedera.node.app.workflows.handle.flow.dispatch;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Account;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public record ErrorReport(
        @NonNull AccountID creatorId,
        @Nullable ResponseCodeEnum creatorError,
        @Nullable Account payer,
        @Nullable ResponseCodeEnum payerSolvencyError,
        boolean unableToPayServiceFee,
        boolean isDuplicate) {

    public static ErrorReport withCreatorError(@NonNull AccountID creatorId, @NonNull ResponseCodeEnum creatorError) {
        return new ErrorReport(creatorId, creatorError, null, null, false, false);
    }

    public static ErrorReport withPayerError(
            @NonNull AccountID creatorId,
            @NonNull Account payer,
            @NonNull ResponseCodeEnum payerError,
            boolean unableToPayServiceFee,
            boolean isDuplicate) {
        return new ErrorReport(creatorId, null, payer, payerError, unableToPayServiceFee, isDuplicate);
    }

    public static ErrorReport withNoError(@NonNull AccountID creatorId, @NonNull Account payer) {
        return new ErrorReport(creatorId, null, payer, null, false, false);
    }

    public boolean isCreatorError() {
        return creatorError != null;
    }

    public boolean isPayerSolvencyError() {
        return payerSolvencyError != null;
    }

    public ResponseCodeEnum payerSolvencyErrorOrThrow() {
        return requireNonNull(payerSolvencyError);
    }

    public ResponseCodeEnum creatorErrorOrThrow() {
        return requireNonNull(creatorError);
    }

    public Account payerOrThrow() {
        return requireNonNull(payer);
    }

    public ErrorReport withoutServiceFee() {
        return new ErrorReport(creatorId, creatorError, payer, payerSolvencyError, true, isDuplicate);
    }
}
