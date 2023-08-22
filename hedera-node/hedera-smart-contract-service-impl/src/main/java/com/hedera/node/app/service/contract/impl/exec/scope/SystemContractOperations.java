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

package com.hedera.node.app.service.contract.impl.exec.scope;

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenRelationship;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.ResultStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public interface SystemContractOperations {
    /**
     * Returns the {@link Nft} with the given id, and also externalizes the result of the state read via a record whose
     * (1) origin is a given contract number and (2) result is derived from the read state via a given
     * {@link ResultTranslator}.
     *
     * @param id                    the NFT id
     * @param callingContractNumber the number of the contract that is calling this method
     * @param translator            the {@link ResultTranslator} that derives the record result from the read state
     * @return the NFT, or {@code null} if no such NFT exists
     */
    @Nullable
    Nft getNftAndExternalizeResult(
            @NonNull NftID id, long callingContractNumber, @NonNull ResultTranslator<Nft> translator);

    /**
     * Returns the {@link Token} with the given number, and also externalizes the result of the state read via a record
     * whose (1) origin is a given contract number and (2) result is derived from the read state via a given
     * {@link ResultTranslator}.
     *
     * @param number                the token number
     * @param callingContractNumber the number of the contract that is calling this method
     * @param translator            the {@link ResultTranslator} that derives the record result from the read state
     * @return the token, or {@code null} if no such token exists
     */
    @Nullable
    Token getTokenAndExternalizeResult(
            long number, long callingContractNumber, @NonNull ResultTranslator<Token> translator);

    /**
     * Returns the {@link Account} with the given number, and also externalizes the result of the state read via a
     * record whose (1) origin is a given contract number and (2) result is derived from the read state via a given
     * {@link ResultTranslator}.
     *
     * @param number                the account number
     * @param callingContractNumber the number of the contract that is calling this method
     * @param translator            the {@link ResultTranslator} that derives the record result from the read state
     * @return the account, or {@code null} if no such account exists
     */
    @Nullable
    Account getAccountAndExternalizeResult(
            long number, long callingContractNumber, @NonNull ResultTranslator<Account> translator);

    /**
     * Returns the {@link TokenRelationship} between the given account and token numbers, and also externalizes the
     * result of the state read via a record whose (1) origin is a given contract number and (2) result is derived from
     * the read state via a given {@link ResultTranslator}.
     *
     * @param accountNumber         the account number in the relationship
     * @param tokenNumber           the token number in the relationship
     * @param callingContractNumber the number of the contract that is calling this method
     * @param translator            the {@link ResultTranslator} that derives the record result from the read state
     * @return the relationship, or {@code null} if no such relationship exists
     */
    @Nullable
    TokenRelationship getRelationshipAndExternalizeResult(
            long accountNumber,
            long tokenNumber,
            long callingContractNumber,
            @NonNull ResultTranslator<TokenRelationship> translator);

    /**
     * Attempts to dispatch the given {@code syntheticTransaction} in the context of the current
     * {@link HandleHederaOperations}, performing signature verification with priority given to the included
     * {@code VerificationStrategy}.
     *
     * <p>If the result is {@code SUCCESS}, but this scope or any of its parents revert, the record
     * of this dispatch should have its stateful side effects cleared and its result set to {@code REVERTED_SUCCESS}.
     *
     * @param syntheticTransaction the synthetic transaction to dispatch
     * @param strategy             the non-cryptographic signature verification to use
     * @return the result of the dispatch
     */
    @NonNull
    ResponseCodeEnum dispatch(@NonNull TransactionBody syntheticTransaction, @NonNull VerificationStrategy strategy);

    /**
     * Attempts to create a child record of the current record, with the given {@code result}
     *
     * @param result    contract function result
     * @param status    whether the result is success or an error
     */
    public void externalizeResult(@NonNull final ContractFunctionResult result, @NonNull final ResultStatus status);
}
