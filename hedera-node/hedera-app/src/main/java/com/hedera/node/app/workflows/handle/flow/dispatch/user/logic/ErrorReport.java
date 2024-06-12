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

package com.hedera.node.app.workflows.handle.flow.dispatch.user.logic;

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
 * @param payerSolvencyError the payer solvency error, if the payer was unable to pay
 * @param unableToPayServiceFee whether the payer was unable to pay the service fee
 * @param isDuplicate whether the transaction is a duplicate
 */
public record ErrorReport(
        @NonNull AccountID creatorId,
        @Nullable ResponseCodeEnum creatorError,
        @Nullable Account payer,
        @Nullable ResponseCodeEnum payerSolvencyError,
        boolean unableToPayServiceFee,
        boolean isDuplicate) {
    /**
     * Creates an error report with a creator error.
     * @param creatorId the creator account ID
     * @param creatorError the creator error
     * @return the error report
     */
    public static ErrorReport withCreatorError(@NonNull AccountID creatorId, @NonNull ResponseCodeEnum creatorError) {
        return new ErrorReport(creatorId, creatorError, null, null, false, false);
    }

    /**
     * Creates an error report with a payer error.
     * @param creatorId the creator account ID
     * @param payer the payer account
     * @param payerError the payer error
     * @param unableToPayServiceFee whether the payer was unable to pay the service fee
     * @param isDuplicate whether the transaction is a duplicate
     * @return the error report
     */
    public static ErrorReport withPayerError(
            @NonNull AccountID creatorId,
            @NonNull Account payer,
            @NonNull ResponseCodeEnum payerError,
            boolean unableToPayServiceFee,
            boolean isDuplicate) {
        return new ErrorReport(creatorId, null, payer, payerError, unableToPayServiceFee, isDuplicate);
    }

    /**
     * Creates an error report with no error.
     * @param creatorId the creator account ID
     * @param payer the payer account
     * @return the error report
     */
    public static ErrorReport withNoError(@NonNull AccountID creatorId, @NonNull Account payer) {
        return new ErrorReport(creatorId, null, payer, null, false, false);
    }

    /**
     * Checks if there is a creator error.
     * @return true if there is a creator error. Otherwise, false.
     */
    public boolean isCreatorError() {
        return creatorError != null;
    }

    /**
     * Checks if there is a payer solvency error.
     * @return true if there is a payer solvency error. Otherwise, false.
     */
    public boolean isPayerSolvencyError() {
        return payerSolvencyError != null;
    }

    /**
     * Checks if there is payer solvency error. If not, throws an exception.
     * @return the payer solvency error
     */
    public ResponseCodeEnum payerSolvencyErrorOrThrow() {
        return requireNonNull(payerSolvencyError);
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
        return new ErrorReport(creatorId, creatorError, payer, payerSolvencyError, true, isDuplicate);
    }
}
