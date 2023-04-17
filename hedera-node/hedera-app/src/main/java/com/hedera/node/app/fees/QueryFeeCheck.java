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

package com.hedera.node.app.fees;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import java.util.List;

/**
 * Collection of checks to be performed on query payment transfers
 */
public interface QueryFeeCheck {

    /**
     * Validates query payment transfer transaction before reaching consensus. Validate each payer has enough balance
     * that is needed for transfer. If one of the payer for query is also paying transactionFee validate the payer has
     * balance to pay both
     *
     * @param txBody the transaction body to validate
     * @param queryFee transaction fee
     * @throws InsufficientBalanceException if the transaction is invalid
     */
    void validateQueryPaymentTransfers(TransactionBody txBody, long queryFee) throws InsufficientBalanceException;

    /**
     * Validates node payment transfer transaction before reaching consensus. Validate each payer has enough balance.
     *
     * @param transfers list of transfers
     * @param queryFee transaction fee
     * @param node account id of the node
     * @throws InsufficientBalanceException if the payments are insufficient
     */
    void nodePaymentValidity(List<AccountAmount> transfers, long queryFee, AccountID node)
            throws InsufficientBalanceException;
}
