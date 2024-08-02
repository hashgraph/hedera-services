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

package com.hedera.node.app.spi.workflows.record;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Base record builder for any transaction that can delete accounts or contracts. These include,
 * <ol>
 *     <li>{@link HederaFunctionality#CRYPTO_DELETE}</li>
 *     <li>{@link HederaFunctionality#CONTRACT_DELETE}</li>
 *     <li>{@link HederaFunctionality#CONTRACT_CREATE}</li>
 *     <li>{@link HederaFunctionality#CONTRACT_CALL}</li>
 *     <li>{@link HederaFunctionality#ETHEREUM_TRANSACTION}</li>
 * </ol>
 *
 * <p>We need to track the beneficiary account id for each deleted account id because if a
 * just-deleted account was going to receive staking rewards in the transaction, those rewards
 * should be redirected to the beneficiary account.
 */
public interface DeleteCapableTransactionStreamBuilder extends StreamBuilder {
    /**
     * Gets number of deleted accounts in this transaction.
     * @return number of deleted accounts in this transaction
     */
    int getNumberOfDeletedAccounts();

    /**
     * Gets the beneficiary account ID for deleted account ID.
     * @return the beneficiary account ID of deleted account ID
     */
    @Nullable
    AccountID getDeletedAccountBeneficiaryFor(@NonNull final AccountID deletedAccountID);

    /**
     * Adds a beneficiary for a deleted account into the map. This is needed while computing staking rewards.
     * If the deleted account receives staking reward, it is transferred to the beneficiary.
     *
     * @param deletedAccountID the deleted account ID
     * @param beneficiaryForDeletedAccount the beneficiary account ID
     */
    void addBeneficiaryForDeletedAccount(
            @NonNull AccountID deletedAccountID, @NonNull AccountID beneficiaryForDeletedAccount);
}
