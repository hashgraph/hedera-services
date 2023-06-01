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

package com.hedera.node.app.spi.meta.bni;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenRelationship;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.common.UniqueTokenId;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Manages state access and mutation within a {@link Scope}.
 *
 * <p>This lets an EVM smart contract make atomic changes scoped to a message frame, even though
 * these changes involve state that it cannot mutate directly via the {@code ContractService}'s
 * {@code WritableStates}.
 */
public interface Dispatch {
    // --- (SECTION I) State access methods that reflect all changes up to and including the current
    // --- {@link Scope}, and ALSO have the side effect of externalizing a result via a record whose
    // --- {@code contractCallResult} has the given sender contract number; and whose result is derived
    // --- from the returned state via a given {@link ResultTranslator}

    /**
     * Returns the {@link Nft} with the given id, and also externalizes the result of the state read
     * via a record whose (1) origin is a given contract number and (2) result is derived from the
     * read state via a given {@link ResultTranslator}.
     *
     * @param id                    the NFT id
     * @param callingContractNumber the number of the contract that is calling this method
     * @param translator            the {@link ResultTranslator} that derives the record result from the read state
     * @return the NFT, or {@code null} if no such NFT exists
     */
    @Nullable
    Nft getNftAndExternalizeResult(
            UniqueTokenId id, long callingContractNumber, @NonNull ResultTranslator<Nft> translator);

    /**
     * Returns the {@link Token} with the given number, and also externalizes the result of the state read
     * via a record whose (1) origin is a given contract number and (2) result is derived from the
     * read state via a given {@link ResultTranslator}.
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
     * Returns the {@link Account} with the given number, and also externalizes the result of the state read
     * via a record whose (1) origin is a given contract number and (2) result is derived from the
     * read state via a given {@link ResultTranslator}.
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
     * Returns the {@link TokenRelationship} between the given account and token numbers, and also
     * externalizes the result of the state read via a record whose (1) origin is a given contract number
     * and (2) result is derived from the read state via a given {@link ResultTranslator}.
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

    // --- (SECTION II) State mutation methods that reflect all the context up to and including the current
    // --- {@link Scope}, that do not have any corresponding synthetic {@code TransactionBody}; and/or have
    // --- such different semantics for signing requirements and record creation that such a dispatch would be
    // --- pointless overhead.

    /**
     * Creates a new hollow account with the given EVM address. The implementation of this call should
     * consume a new entity number for the created new account.
     * <p>
     * If this fails due to some non-EVM resource constraints on number of preceding child records (or even on
     * the total number of accounts in state), should return the appropriate failure code, and
     * {@link ResponseCodeEnum#OK} otherwise.
     *
     * @param evmAddress the EVM address of the new hollow account
     * @return the result of the creation
     */
    ResponseCodeEnum createHollowAccount(@NonNull Bytes evmAddress);

    /**
     * Finalizes an existing hollow account with the given address as a contract by setting
     * {@code isContract=true}, {@code key=Key{contractID=...}}, and {@code nonce=1}. As with
     * a "normal" internal {@code CONTRACT_CREATION}, the record of this finalization should
     * only be externalized if the top-level HAPI transaction succeeds.
     *
     * @param evmAddress the EVM address of the hollow account to finalize as a contract
     */
    void finalizeHollowAccountAsContract(@NonNull Bytes evmAddress);

    /**
     * Transfers value from one account or contract to another without creating a record in this {@link Scope},
     * performing signature verification for a receiver with {@code receiverSigRequired=true} by giving priority
     * to the included {@code VerificationStrategy}.
     *
     * @param amount           the amount to transfer
     * @param fromEntityNumber the number of the entity to transfer from
     * @param toEntityNumber   the number of the entity to transfer to
     * @param strategy         the {@link VerificationStrategy} to use
     * @return the result of the transfer attempt
     */
    ResponseCodeEnum transferValue(
            long amount, long fromEntityNumber, long toEntityNumber, @NonNull VerificationStrategy strategy);

    /**
     * Links the given {@code evmAddress} to the given {@code entityNumber} as an alias.
     *
     * <p>The entity number does not have to exist yet, since during a {@code CREATE} or {@code CREATE2}
     * flow we "speculatively" create the pending contract's alias to more naturally implement the API
     * assumed by the Besu {@code ContractCreationProcessor}.
     *
     * @param evmAddress   the EVM address to link
     * @param entityNumber the entity number to link to
     */
    void setAlias(@NonNull Bytes evmAddress, long entityNumber);

    /**
     * Assigns the given {@code nonce} to the given {@code contractNumber}.
     *
     * @param contractNumber the contract number
     * @param nonce          the new nonce
     * @throws IllegalArgumentException if there is no valid contract with the given {@code contractNumber}
     */
    void setNonce(long contractNumber, long nonce);

    /**
     * Returns what will be the next new entity number.
     *
     * @return the next entity number
     */
    long peekNextEntityNumber();

    /**
     * Reserves a new entity number for a contract being created.
     *
     * @return the reserved entity number
     */
    long useNextEntityNumber();

    /**
     * Creates a new contract with the given entity number and EVM address; and also "links" the alias.
     *
     * <p>Any inheritable Hedera-native properties managed by the {@code TokenService} should be set on
     * the new contract based on the given model account.
     *
     * <p>The record of this creation should only be externalized if the top-level HAPI transaction succeeds.
     *
     * @param number       the number of the contract to create
     * @param parentNumber the number of the contract whose properties the new contract should inherit
     * @param nonce        the nonce of the contract to create
     * @param evmAddress   if not null, the EVM address to use as an alias of the created contract
     */
    void createContract(long number, long parentNumber, long nonce, @Nullable Bytes evmAddress);

    /**
     * Deletes the contract whose alias is the given {@code evmAddress}, and also "unlinks" the alias.
     * Signing requirements are waived, and the record of this deletion should only be externalized if
     * the top-level HAPI transaction succeeds.
     *
     * <p>The record of this creation should only be externalized if the top-level HAPI transaction succeeds.
     *
     * @param evmAddress the EVM address of the contract to delete
     */
    void deleteAliasedContract(@NonNull Bytes evmAddress);

    /**
     * Convenience method to delete an unaliased contract with the given number.
     *
     * @param number the number of the contract to delete
     */
    void deleteUnaliasedContract(long number);

    /**
     * Updates the storage metadata for the given contract.
     *
     * @param contractNumber the number of the contract
     * @param firstKey       the first key in the storage linked list, or {@code null} if the list is empty
     * @param slotsUsed      the number of storage slots used by the contract
     */
    void updateStorageMetadata(long contractNumber, @Nullable Bytes firstKey, int slotsUsed);

    /**
     * Attempts to charge the given {@code amount} of rent to the given {@code contractNumber}, with
     * preference to its auto renew account (if any); falling back to charging the contract itself
     * if the auto renew account does not exist or does not have sufficient balance.
     *
     * @param contractNumber         the number of the contract to charge
     * @param amount                 the amount to charge
     * @param itemizeStoragePayments whether to itemize storage payments in the record
     */
    ResponseCodeEnum chargeStorageRent(long contractNumber, long amount, boolean itemizeStoragePayments);

    // --- (SECTION III) A state mutation method that dispatches a synthetic {@code TransactionBody} within
    // --- the context of the current {@code Scope}, performing signature verification with priority given to the
    // --- provided {@code VerificationStrategy}.

    /**
     * Attempts to dispatch the given {@code syntheticTransaction} in the context of the current
     * {@link Scope}, performing signature verification with priority given to the included
     * {@code VerificationStrategy}.
     *
     * <p>If the result is {@code SUCCESS}, but this scope or any of its parents revert, the record
     * of this dispatch should have its stateful side effects cleared and its result set to
     * {@code REVERTED_SUCCESS}.
     *
     * @param syntheticTransaction the synthetic transaction to dispatch
     * @param strategy             the non-cryptographic signature verification to use
     * @return the result of the dispatch
     */
    ResponseCodeEnum dispatch(@NonNull TransactionBody syntheticTransaction, @NonNull VerificationStrategy strategy);

    // --- (SECTION IV) Read-only methods that reflects all changes up to and including the current {@link Scope}

    /**
     * Given an EVM address, resolves to the account or contract number (if any) that this address
     * is an alias for.
     *
     * @param evmAddress the EVM address
     * @return the account or contract number, or {@code null} if the address is not an alias
     */
    @Nullable
    EntityNumber resolveAlias(@NonNull Bytes evmAddress);

    /**
     * Returns the {@link Account} with the given number.
     *
     * @param number the account number
     * @return the account, or {@code null} if no such account exists
     */
    @Nullable
    Account getAccount(long number);

    /**
     * Returns a list of the account numbers that have been modified in this scope.
     *
     * @return the list of modified account numbers
     */
    List<Long> getModifiedAccountNumbers();

    /**
     * Returns the {@link Token} with the given number.
     *
     * @param number the token number
     * @return the token, or {@code null} if no such token exists
     */
    @Nullable
    Token getToken(long number);

    /**
     * Returns the {@link Nft} with the given id.
     *
     * @param id the NFT id
     * @return the NFT, or {@code null} if no such NFT exists
     */
    @Nullable
    Nft getNft(@NonNull UniqueTokenId id);
}
