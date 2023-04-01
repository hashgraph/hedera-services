/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.prehandle;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import static java.util.Objects.requireNonNull;

/**
 * Metadata collected when transactions are handled as part of "pre-handle". This happens with
 * multiple background threads. Any state read or computed as part of this pre-handle, including any
 * errors, are captured in the TransactionMetadata. This is then made available to the transaction
 * during the "handle" phase as part of the HandleContext.
 *
 * @param txnInfo Parsed information related to the transaction
 * @param payer payer for the transaction (might be different from the payer in the transaction ID)
 * @param status {@link ResponseCodeEnum} status of the transaction
 * @param innerMetadata {@link PreHandleResult} of the inner transaction (where appropriate)
 * @param unhandledException If some unexpected exception happened during pre-handle, it is captured here.
 */
public record PreHandleResult(
        @Nullable TransactionInfo txnInfo,
        @Nullable AccountID payer,
        @NonNull ResponseCodeEnum status,
        @Nullable PreHandleResult innerMetadata,
        @Nullable Exception unhandledException) {

    public static PreHandleResult nodeDueDiligenceFailure(@NonNull final AccountID creator, @NonNull final ResponseCodeEnum status) {
        return new PreHandleResult(null, creator, status, null, null);
    }

    public static PreHandleResult nodeDueDiligenceFailure(@NonNull final AccountID creator, @NonNull final ResponseCodeEnum status, @NonNull final TransactionInfo info) {
        return new PreHandleResult(info, creator, status, null, null);
    }

    public static PreHandleResult preHandleFailure(@NonNull final AccountID payer, @NonNull final ResponseCodeEnum status, @NonNull final TransactionInfo info) {
        return new PreHandleResult(info, payer, status, null, null);
    }

    public static PreHandleResult unknownFailure(@NonNull final AccountID creator, @NonNull final Exception e) {
        return new PreHandleResult(null, creator, ResponseCodeEnum.UNKNOWN, null, e);
    }

    public static PreHandleResult unknownFailure(@NonNull final AccountID creator, @NonNull final Exception e, @NonNull final TransactionInfo info) {
        return new PreHandleResult(info, creator, ResponseCodeEnum.UNKNOWN, null, e);
    }

    /**
     * Checks the failure by validating the status is not {@link ResponseCodeEnum OK}
     *
     * @return returns true if status is not OK
     */
    public boolean failed() {
        return status != ResponseCodeEnum.OK;
    }
}
