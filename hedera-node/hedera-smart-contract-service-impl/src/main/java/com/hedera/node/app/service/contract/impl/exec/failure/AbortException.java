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

package com.hedera.node.app.service.contract.impl.exec.failure;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * An exception thrown when a transaction is aborted before entering the EVM.
 *
 * <p>Includes the effective Hedera id of the sender.
 */
public class AbortException extends HandleException {
    private final AccountID senderId;

    @Nullable
    private final AccountID relayerId;
    // Whether gas can still be charged for the transaction
    private final boolean isChargeable;

    /**
     * @param status the status of the exception
     * @param senderId the sender of the transaction
     */
    public AbortException(@NonNull final ResponseCodeEnum status, @NonNull final AccountID senderId) {
        super(status);
        this.senderId = requireNonNull(senderId);
        this.relayerId = null;
        this.isChargeable = false;
    }

    /**
     * @param status the status of the exception
     * @param senderId the sender of the transaction
     * @param relayerId the relayer of the transaction
     * @param isChargeable true if the gas will still be charged
     */
    public AbortException(
            @NonNull final ResponseCodeEnum status,
            @NonNull final AccountID senderId,
            @Nullable final AccountID relayerId,
            final boolean isChargeable) {
        super(status);
        this.senderId = requireNonNull(senderId);
        this.relayerId = relayerId;
        this.isChargeable = isChargeable;
    }

    /**
     * Returns the effective Hedera id of the sender.
     *
     * @return the effective Hedera id of the sender
     */
    public AccountID senderId() {
        return senderId;
    }

    /**
     * Returns whether the transaction can still be charged for gas.
     *
     * @return whether the transaction can still be charged for gas.
     */
    public boolean isChargeable() {
        return isChargeable;
    }

    /**
     * Returns the relayer id.
     *
     * @return the relayer id.
     */
    @Nullable
    public AccountID relayerId() {
        return relayerId;
    }

    /**
     * Throws an {@code AbortException} if the given flag is {@code false}.
     *
     * @param flag the flag to check
     * @param status the status to use if the flag is {@code false}
     * @param senderId the effective Hedera id of the sender
     */
    public static void validateTrueOrAbort(
            final boolean flag, @NonNull final ResponseCodeEnum status, @NonNull final AccountID senderId) {
        if (!flag) {
            throw new AbortException(status, senderId);
        }
    }
}
