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
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Map;

/**
 * Context for the current CryptoTransfer transaction.
 * Each CryptoTransfer transaction goes through different steps in handling. The output of one step will
 * be needed as input to other steps. For example, in the first step we resolve all the aliases in the transaction body.
 * The resolutions are needed in further steps to is IDs instead of aliases.
 * It also has helper function to create accounts from alias.
 * This class stores all the needed information that is shared between steps in handling a CryptoTransfer transaction.
 * The lifecycle of this clas is the same as the lifecycle of a CryptoTransfer transaction.
 */
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

    HandleContext getHandleContext();
}
