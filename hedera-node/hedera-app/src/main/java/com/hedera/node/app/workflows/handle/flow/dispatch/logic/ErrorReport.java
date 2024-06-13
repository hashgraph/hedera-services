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

package com.hedera.node.app.workflows.handle.flow.dispatch.logic;

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Account;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A report of errors that occurred during the processing of a transaction. This records the errors including the
 * creator error, the payer error, and whether the payer was unable to pay the service fee. It also records whether
 * the transaction is a duplicate.
 * @param creatorId the creator account ID, should be non-null
 * @param creatorError the creator error, can be null if there is no creator error
 * @param payer the payer account, can be null if there is no payer account for contract operations. It can be a token address.
 * @param payerError the final determined payer error, if any
 * @param unableToPayServiceFee whether the payer was unable to pay the service fee
 * @param duplicateStatus whether the transaction is a duplicate
 */
public record ErrorReport(
        @NonNull AccountID creatorId,
        @Nullable ResponseCodeEnum creatorError,
        @Nullable Account payer,
        @Nullable ResponseCodeEnum payerError,
        boolean unableToPayServiceFee,
        @NonNull DuplicateStatus duplicateStatus) {
    /**
     * Creates an error report with a creator error.
     * @param creatorId the creator account ID
     * @param creatorError the creator error
     * @return the error report
     */
    public static ErrorReport creatorErrorReport(@NonNull AccountID creatorId, @NonNull ResponseCodeEnum creatorError) {
        return new ErrorReport(creatorId, creatorError, null, null, false, DuplicateStatus.NO_DUPLICATE);
    }

    /**
     * Creates an error report with a payer error due to a duplicate transaction.
     *
     * @param creatorId the creator account ID
     * @param payer the payer account
     * @return the error report
     */
    public static ErrorReport payerDuplicateErrorReport(
            @NonNull final AccountID creatorId, @NonNull final Account payer) {
        requireNonNull(payer);
        requireNonNull(creatorId);
        return new ErrorReport(creatorId, null, payer, DUPLICATE_TRANSACTION, false, DuplicateStatus.DUPLICATE);
    }

    /**
     * Creates an error report with a payer error due to a reason other than a duplicate transaction
     * or a solvency failure.
     *
     * @param creatorId the creator account ID
     * @param payer the payer account
     * @return the error report
     */
    public static ErrorReport payerUniqueErrorReport(
            @NonNull final AccountID creatorId,
            @NonNull final Account payer,
            @NonNull final ResponseCodeEnum payerError) {
        requireNonNull(payer);
        requireNonNull(creatorId);
        requireNonNull(payerError);
        return new ErrorReport(creatorId, null, payer, payerError, false, DuplicateStatus.NO_DUPLICATE);
    }

    /**
     * Creates an error report with a payer error.
     * @param creatorId the creator account ID
     * @param payer the payer account
     * @param payerError the payer error
     * @param unableToPayServiceFee whether the payer was unable to pay the service fee
     * @param duplicateStatus whether the transaction is a duplicate
     * @return the error report
     */
    public static ErrorReport payerErrorReport(
            @NonNull AccountID creatorId,
            @NonNull Account payer,
            @NonNull ResponseCodeEnum payerError,
            boolean unableToPayServiceFee,
            @NonNull final DuplicateStatus duplicateStatus) {
        return new ErrorReport(creatorId, null, payer, payerError, unableToPayServiceFee, duplicateStatus);
    }

    /**
     * Creates an error report with no error.
     * @param creatorId the creator account ID
     * @param payer the payer account
     * @return the error report
     */
    public static ErrorReport errorFreeReport(@NonNull AccountID creatorId, @NonNull Account payer) {
        return new ErrorReport(creatorId, null, payer, null, false, DuplicateStatus.NO_DUPLICATE);
    }

    /**
     * Checks if there is a creator error.
     * @return true if there is a creator error. Otherwise, false.
     */
    public boolean isCreatorError() {
        return creatorError != null;
    }

    /**
     * Checks if there is a payer error.
     * @return true if there is a payer error. Otherwise, false.
     */
    public boolean isPayerError() {
        return payerError != null;
    }

    /**
     * Checks if there is payer error. If not, throws an exception.
     * @return the payer error
     */
    public ResponseCodeEnum payerErrorOrThrow() {
        return requireNonNull(payerError);
    }

    /**
     * Checks if there is a creator error. If not, throws an exception.
     * @return the creator error
     */
    public ResponseCodeEnum creatorErrorOrThrow() {
        return requireNonNull(creatorError);
    }

    /**
     * Checks if there is a payer.
     * @return payer account if there is a payer. Otherwise, throws an exception.
     */
    public Account payerOrThrow() {
        return requireNonNull(payer);
    }

    /**
     * Returns the error report with all fees except service fee.
     * @return the error report
     */
    public ErrorReport withoutServiceFee() {
        return new ErrorReport(creatorId, creatorError, payer, payerError, true, duplicateStatus);
    }
}
