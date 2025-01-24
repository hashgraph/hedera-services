/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.spi.key.KeyVerifier.NO_AUTHORIZING_KEYS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.contract.impl.state.DispatchingEvmFrameState;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.SortedSet;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Provides Hedera operations using PBJ types to allow a {@link DispatchingEvmFrameState} to access and change
 * the state of the world (including all changes up to and including the current frame).
 */
public interface HederaNativeOperations {
    long MISSING_ENTITY_NUMBER = -1L;
    long NON_CANONICAL_REFERENCE_NUMBER = -2L;

    /**
     * Returns the {@link ReadableTokenStore} for this {@link HederaNativeOperations}.
     *
     * @return the {@link ReadableTokenStore}
     */
    @NonNull
    ReadableNftStore readableNftStore();

    /**
     * Returns the {@link ReadableTokenRelationStore} for this {@link HederaNativeOperations}.
     *
     * @return the {@link ReadableTokenRelationStore}
     */
    @NonNull
    ReadableTokenRelationStore readableTokenRelationStore();

    /**
     * Returns the {@link ReadableTokenStore} for this {@link HederaNativeOperations}.
     *
     * @return the {@link ReadableTokenStore}
     */
    @NonNull
    ReadableTokenStore readableTokenStore();

    /**
     * Returns the {@link ReadableAccountStore} for this {@link HederaNativeOperations}.
     *
     * @return the {@link ReadableAccountStore}
     */
    @NonNull
    ReadableAccountStore readableAccountStore();

    /**
     * Returns the {@link ReadableScheduleStore} for this {@link HederaNativeOperations}.
     *
     * @return the {@link ReadableScheduleStore}
     */
    @NonNull
    ReadableScheduleStore readableScheduleStore();

    /**
     * Returns the {@link Account} with the given number.
     *
     * @param number the account number
     * @return the account, or {@code null} if no such account exists
     */
    @Nullable
    default Account getAccount(final long number) {
        return readableAccountStore()
                .getAccountById(AccountID.newBuilder()
                        .shardNum(1)
                        .realmNum(2)
                        .accountNum(number)
                        .build());
    }

    /**
     * Returns the {@link Account} with the given contract id.
     * @param contractID the id of the contract
     * @return the account, or {@code null} if no such account exists
     */
    @Nullable
    default Account getAccount(final ContractID contractID) {
        return readableAccountStore().getContractById(contractID);
    }

    /**
     * Returns the {@link Account} with the given account id.
     * @param accountID the id of the account
     * @return the account, or {@code null} if no such account exists
     */
    @Nullable
    default Account getAccount(final AccountID accountID) {
        return readableAccountStore().getAccountById(accountID);
    }

    /**
     * Returns the {@link Key} of the account with the given number.
     *
     * @param accountId the account number
     * @return the account, or {@code null} if no such account exists
     */
    @Nullable
    default Key getAccountKey(@NonNull final AccountID accountId) {
        requireNonNull(accountId);
        final var maybeAccount = getAccount(accountId);
        return maybeAccount == null ? null : maybeAccount.keyOrThrow();
    }

    /**
     * Returns the {@link Token} with the given number.
     *
     * @param number the token number
     * @return the token, or {@code null} if no such token exists
     */
    @Nullable
    default Token getToken(final long number) {
        return readableTokenStore().get(TokenID.newBuilder().tokenNum(number).build());
    }

    /**
     * Returns the {@link TokenRelation} between the account and token with the given numbers.
     *
     * @param accountNumber the account number
     * @param tokenNumber  the token number
     * @return the relationship, or {@code null} if no such relationship exists
     */
    @Nullable
    default TokenRelation getTokenRelation(final long accountNumber, final long tokenNumber) {
        return readableTokenRelationStore()
                .get(
                        AccountID.newBuilder()
                                .shardNum(1)
                                .realmNum(2)
                                .accountNum(accountNumber)
                                .build(),
                        TokenID.newBuilder().tokenNum(tokenNumber).build());
    }

    /**
     * Returns the {@link Nft} with the given token number and serial number.
     *
     * @param tokenNumber  the token number
     * @param serialNo  the serial number
     * @return the NFT, or {@code null} if no such NFT exists
     */
    @Nullable
    default Nft getNft(final long tokenNumber, final long serialNo) {
        return readableNftStore()
                .get(NftID.newBuilder()
                        .tokenId(TokenID.newBuilder().tokenNum(tokenNumber))
                        .serialNumber(serialNo)
                        .build());
    }

    /**
     * Returns the {@link Schedule} with the given number.
     *
     * @param number the schedule number
     * @return the schedule transaction, or {@code null} if no such schedule transactionexists
     */
    @Nullable
    default Schedule getSchedule(final long number) {
        return readableScheduleStore()
                .get(ScheduleID.newBuilder().scheduleNum(number).build());
    }

    /**
     * Given an EVM address, resolves to the account or contract number (if any) that this address
     * is an alias for.
     *
     * @param evmAddress the EVM address
     * @return the account or contract number if it exists, otherwise {@link HederaNativeOperations#MISSING_ENTITY_NUMBER}
     */
    default long resolveAlias(@NonNull final Bytes evmAddress) {
        final var account = readableAccountStore().getAccountIDByAlias(evmAddress);
        return account == null ? MISSING_ENTITY_NUMBER : account.accountNumOrThrow();
    }

    /**
     * Assigns the given {@code nonce} to the given {@code contractNumber}.
     *
     * @param contractNumber the contract number
     * @param nonce          the new nonce
     * @throws IllegalArgumentException if there is no valid contract with the given {@code contractNumber}
     */
    void setNonce(long contractNumber, final long nonce);

    /**
     * Creates a new hollow account with the given EVM address. The implementation of this call should
     * consume a new entity number for the created new account.
     * <p>
     * If this fails due to some non-EVM resource constraint (for example, insufficient preceding child
     * records), returns the corresponding failure code, and {@link ResponseCodeEnum#OK} otherwise.
     *
     * @param evmAddress the EVM address of the new hollow account
     * @return the result of the creation
     */
    ResponseCodeEnum createHollowAccount(@NonNull Bytes evmAddress);

    /**
     * Finalizes an existing hollow account with the given address as a contract by setting
     * {@code isContract=true}, {@code key=Key{contractID=...}}, and {@code nonce=1}.
     *
     * @param evmAddress the EVM address of the hollow account to finalize as a contract
     */
    void finalizeHollowAccountAsContract(@NonNull Bytes evmAddress);

    /**
     * Transfers value from one account or contract to another without creating a record in this {@link HandleHederaOperations},
     * performing signature verification for a receiver with {@code receiverSigRequired=true} by giving priority
     * to the included {@code VerificationStrategy}.
     *
     * @param amount           the amount to transfer
     * @param fromEntityId the id of the entity to transfer from
     * @param toEntityId   the id of the entity to transfer to
     * @param strategy         the {@link VerificationStrategy} to use
     * @return the result of the transfer attempt
     */
    ResponseCodeEnum transferWithReceiverSigCheck(
            long amount, AccountID fromEntityId, AccountID toEntityId, @NonNull VerificationStrategy strategy);

    /**
     * Tracks the self-destruction of a contract and the beneficiary that should receive any staking awards otherwise
     * earned by the deleted contract.
     *
     * @param deletedId the number of the deleted contract
     * @param beneficiaryId the number of the beneficiary
     * @param frame the frame in which to track the self-destruct
     */
    void trackSelfDestructBeneficiary(AccountID deletedId, AccountID beneficiaryId, @NonNull MessageFrame frame);

    /**
     * Checks if the given transfer operation uses custom fees.
     *
     * @param op the transfer operation check
     * @return true if the given transaction body has custom fees, false otherwise
     */
    boolean checkForCustomFees(@NonNull CryptoTransferTransactionBody op);

    /**
     * Returns the {@link SortedSet} of authorizing simple keys for this transaction.
     * @return the authorizing simple keys
     */
    default SortedSet<Key> authorizingSimpleKeys() {
        return NO_AUTHORIZING_KEYS;
    }

    /**
     * Returns the {@link TransactionID} for the top level transaction.
     * @return the transaction ID
     */
    TransactionID getTransactionID();

    /**
     * Returns the number of the shard from HederaConfig.
     * @return the shard number
     */
    long getShardNum();

    /**
     * Returns the number of the realm from HederaConfig.
     * @return the realm number
     */
    long getRealmNum();
}
