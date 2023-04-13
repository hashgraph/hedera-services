/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.solvency;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Determines if the payer account set in the {@code TransactionID} is expected to be both willing and able to pay the
 * transaction fees.
 *
 * <p>For more details, please see
 * <a href="https://github.com/hashgraph/hedera-services/blob/master/docs/transaction-prechecks.md">...</a>
 */
public interface SolvencyPreCheck {

    /**
     * Checks if the payer account is valid.
     *
     * @param state the {@link HederaState} to use during the check
     * @param accountID the {@link AccountID} of the payer
     * @throws PreCheckException if the payer account is invalid
     */
    void checkPayerAccountStatus(@NonNull HederaState state, @NonNull AccountID accountID) throws PreCheckException;

    /**
     * Checks if the verified payer account of the given transaction can afford to cover its fees (with the option to
     * include or exclude the service component).
     *
     * @param state the {@link HederaState} to use during the check
     * @param transaction the {@link Transaction} to check
     * @throws InsufficientBalanceException if the payer account cannot afford the fees. The exception will have a
     * status of {@code INSUFFICIENT_TX_FEE} and the fee amount that would have satisfied the check.
     */
    void checkSolvencyOfVerifiedPayer(@NonNull HederaState state, @NonNull Transaction transaction)
            throws InsufficientBalanceException;
}
