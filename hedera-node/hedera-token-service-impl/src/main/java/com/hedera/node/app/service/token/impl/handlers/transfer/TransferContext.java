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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Map;

public interface TransferContext {
    /**
     * Looks up alias from accountID in form of alias and return the account ID with account number if found.
     * Return null otherwise.
     * @param aliasedId the account ID with the account number associated with alias
     * @return the account ID with account number if found, null otherwise
     */
    AccountID getFromAlias(AccountID aliasedId);

    /**
     * Creates an account from the given alias. This is called when the account associated with alias
     * is not found in the account store
     * @param alias the alias of the account
     * @param isFromTokenTransfer true if the account is created from token transfer, false otherwise
     */
    void createFromAlias(Bytes alias, boolean isFromTokenTransfer);

    /**
     * Returns the number of auto-creation of accounts in current transfer
     * @return the number of auto-creation of accounts
     */
    int numOfAutoCreations();

    /**
     * Returns the number of lazy-creation of accounts in current transfer
     * @return the number of lazy-creation of accounts
     */
    int numOfLazyCreations();

    /**
     * Returns the resolved accounts with alias and its account ID
     * @return the resolved accounts with alias and its account ID
     */
    Map<Bytes, AccountID> resolutions();

    // Throw if the fee cannot be charged for whatever reason
    void chargeExtraFeeToHapiPayer(long amount);

    void chargeCustomFeeTo(AccountID payer, long amount, TokenID denomination);

    // Debit an account based on the HAPI payer having an approved allowance from the given owner
    default void debitHbarViaApproval(AccountID owner, long amount) {}

    HandleContext getHandleContext();
}
