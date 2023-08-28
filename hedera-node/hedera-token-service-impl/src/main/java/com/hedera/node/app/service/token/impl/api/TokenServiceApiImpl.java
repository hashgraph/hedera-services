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

package com.hedera.node.app.service.token.impl.api;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator.IMMUTABILITY_SENTINEL_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.contract.ContractNonceInfo;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.FeeRecordBuilder;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.validators.StakingValidator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements {@link TokenServiceApi} via {@link WritableAccountStore} calls.
 */
public class TokenServiceApiImpl implements TokenServiceApi {
    private static final Logger logger = LogManager.getLogger(TokenServiceApiImpl.class);
    private static final Key STANDIN_CONTRACT_KEY =
            Key.newBuilder().contractID(ContractID.newBuilder().contractNum(0)).build();

    private final StakingValidator stakingValidator;
    private final WritableAccountStore store;
    private final AccountID fundingAccountID;
    private final AccountID stakingRewardAccountID;
    private final AccountID nodeRewardAccountID;
    private final StakingConfig stakingConfig;

    public TokenServiceApiImpl(
            @NonNull final Configuration config,
            @NonNull final StakingValidator stakingValidator,
            @NonNull final WritableStates writableStates) {
        requireNonNull(config);
        this.store = new WritableAccountStore(writableStates);
        this.stakingValidator = requireNonNull(stakingValidator);

        // Determine whether staking is enabled
        stakingConfig = config.getConfigData(StakingConfig.class);

        // Compute the account ID's for funding (normally 0.0.98) and staking rewards (normally 0.0.800).
        final var hederaConfig = config.getConfigData(HederaConfig.class);
        fundingAccountID = AccountID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .accountNum(config.getConfigData(LedgerConfig.class).fundingAccount())
                .build();
        stakingRewardAccountID = AccountID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .accountNum(config.getConfigData(AccountsConfig.class).stakingRewardAccount())
                .build();
        nodeRewardAccountID = AccountID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .accountNum(config.getConfigData(AccountsConfig.class).nodeRewardAccount())
                .build();
    }

    @Override
    public void assertValidStakingElection(
            final boolean isStakingEnabled,
            final boolean hasDeclineRewardChange,
            @NonNull final String stakedIdKind,
            @Nullable final AccountID stakedAccountIdInOp,
            @Nullable final Long stakedNodeIdInOp,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final NetworkInfo networkInfo) {
        stakingValidator.validateStakedIdForCreation(
                isStakingEnabled,
                hasDeclineRewardChange,
                stakedIdKind,
                stakedAccountIdInOp,
                stakedNodeIdInOp,
                accountStore,
                networkInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markAsContract(@NonNull final AccountID accountId) {
        requireNonNull(accountId);
        final var accountAsContract = requireNonNull(store.get(accountId))
                .copyBuilder()
                .smartContract(true)
                .build();
        store.put(accountAsContract);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finalizeHollowAccountAsContract(@NonNull final AccountID hollowAccountId, final long initialNonce) {
        requireNonNull(hollowAccountId);
        final var hollowAccount = requireNonNull(store.get(hollowAccountId));
        if (!IMMUTABILITY_SENTINEL_KEY.equals(hollowAccount.keyOrThrow())) {
            throw new IllegalArgumentException(
                    "Cannot finalize non-hollow account " + hollowAccountId + " as contract");
        }
        final var accountAsContract = hollowAccount
                .copyBuilder()
                .key(STANDIN_CONTRACT_KEY)
                .smartContract(true)
                .ethereumNonce(initialNonce)
                .build();
        store.put(accountAsContract);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAndMaybeUnaliasContract(@NonNull final ContractID contractId) {
        requireNonNull(contractId);
        final var contract = requireNonNull(store.getContractById(contractId));

        final var evmAddress = contract.alias();
        final var usedEvmAddress = contractId.evmAddressOrElse(Bytes.EMPTY);
        if (!usedEvmAddress.equals(evmAddress)) {
            logger.error(
                    "Contract {} has an alias {} different than its referencing EVM address {}",
                    contractId,
                    evmAddress,
                    usedEvmAddress);
        }
        maybeRemoveAlias(store, evmAddress);
        maybeRemoveAlias(store, usedEvmAddress);

        store.put(contract.copyBuilder().alias(Bytes.EMPTY).deleted(true).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementParentNonce(@NonNull final ContractID parentId) {
        requireNonNull(parentId);
        final var contract = requireNonNull(store.getContractById(parentId));
        store.put(contract.copyBuilder()
                .ethereumNonce(contract.ethereumNonce() + 1)
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementSenderNonce(@NonNull final AccountID senderId) {
        requireNonNull(senderId);
        final var sender = requireNonNull(store.get(senderId));
        store.put(sender.copyBuilder().ethereumNonce(sender.ethereumNonce() + 1).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNonce(@NonNull final AccountID accountId, final long nonce) {
        requireNonNull(accountId);
        final var target = requireNonNull(store.get(accountId));
        store.put(target.copyBuilder().ethereumNonce(nonce).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void transferFromTo(@NonNull AccountID fromId, @NonNull AccountID toId, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "Cannot transfer negative value (" + amount + " tinybars) from " + fromId + " to " + toId);
        }
        final var from = requireNonNull(store.get(fromId));
        final var to = requireNonNull(store.get(toId));
        if (from.tinybarBalance() < amount) {
            throw new IllegalArgumentException(
                    "Insufficient balance to transfer " + amount + " tinybars from " + fromId + " to " + toId);
        }
        if (to.tinybarBalance() + amount < 0) {
            throw new IllegalArgumentException(
                    "Overflow on transfer of " + amount + " tinybars from " + fromId + " to " + toId);
        }
        store.put(from.copyBuilder()
                .tinybarBalance(from.tinybarBalance() - amount)
                .build());
        store.put(to.copyBuilder().tinybarBalance(to.tinybarBalance() + amount).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<AccountID> modifiedAccountIds() {
        return store.modifiedAccountsInState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ContractNonceInfo> updatedContractNonces() {
        return store.updatedContractNonces();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateStorageMetadata(
            @NonNull final AccountID accountId, @NonNull final Bytes firstKey, final int netChangeInSlotsUsed) {
        requireNonNull(firstKey);
        requireNonNull(accountId);
        final var target = requireNonNull(store.get(accountId));
        if (!target.smartContract()) {
            throw new IllegalArgumentException("Cannot update storage metadata for non-contract " + accountId);
        }
        final var newNumKvPairs = target.contractKvPairsNumber() + netChangeInSlotsUsed;
        if (newNumKvPairs < 0) {
            throw new IllegalArgumentException("Cannot change # of storage slots (currently "
                    + target.contractKvPairsNumber()
                    + ") by "
                    + netChangeInSlotsUsed
                    + " for contract "
                    + accountId);
        }
        store.put(target.copyBuilder()
                .firstContractStorageKey(firstKey)
                .contractKvPairsNumber(newNumKvPairs)
                .build());
    }

    private void maybeRemoveAlias(@NonNull final WritableAccountStore store, @NonNull final Bytes alias) {
        if (!Bytes.EMPTY.equals(alias)) {
            store.removeAlias(alias);
        }
    }

    @Override
    public void chargeFees(@NonNull AccountID payer, @NonNull Fees fees, @NonNull final FeeRecordBuilder rb) {
        // Note: these four accounts (payer, funding, staking reward, node reward) MUST exist for the transaction to be
        // valid and for fees to be processed. If any of them do not exist, the entire transaction will fail. There is
        // no conceivable way that these accounts *should* be null at this point.

        // Record the total fee into the record builder
        final var total = fees.totalFee();
        rb.transactionFee(rb.transactionFee() + total);

        // Charge the payer for the fees
        chargePayer(payer, total);

        // We may have a rounding error, so we will first remove the node and staking rewards from the total, and then
        // whatever is left over goes to the funding account.
        var balance = total;

        // We only pay node and staking rewards if the feature is enabled
        if (stakingConfig.isEnabled()) {
            final var nodeReward = (long) ((stakingConfig.feesNodeRewardPercentage() / 100.0) * total);
            balance -= nodeReward;
            payNodeRewardAccount(nodeReward);

            final var stakingReward = (long) ((stakingConfig.feesStakingRewardPercentage() / 100.0) * total);
            balance -= stakingReward;
            payStakingRewardAccount(stakingReward);
        }

        // Whatever is left over goes to the funding account
        final var fundingAccount = lookupAccount("Funding", fundingAccountID);
        store.put(fundingAccount
                .copyBuilder()
                .tinybarBalance(fundingAccount.tinybarBalance() + balance)
                .build());
    }

    @Override
    public void refundFees(@NonNull AccountID receiver, @NonNull Fees fees, @NonNull final FeeRecordBuilder rb) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * A utility method that charges (debits) the payer for the given total fee. If the payer account doesn't exist,
     * then an exception is thrown.
     *
     * @param payer the account to charge
     * @param amount the amount to charge
     * @throws IllegalStateException if the payer account doesn't exist
     */
    private void chargePayer(@NonNull final AccountID payer, final long amount) {
        final var payerAccount = lookupAccount("Payer", payer);
        final var currentBalance = payerAccount.tinybarBalance();
        if (currentBalance < amount) {
            throw new HandleException(INSUFFICIENT_PAYER_BALANCE);
        }
        store.put(payerAccount
                .copyBuilder()
                .tinybarBalance(currentBalance - amount)
                .build());
    }

    /**
     * Pays the node reward account the given amount. If the node reward account doesn't exist, an exception is thrown.
     * This account *should* have been created at genesis, so it should always exist, even if staking rewards are
     * disabled.
     *
     * @param amount The amount to credit the node reward account.
     * @throws IllegalStateException if the node rewards account doesn't exist
     */
    private void payNodeRewardAccount(final long amount) {
        if (amount == 0) return;
        final var nodeAccount = lookupAccount("Node reward", nodeRewardAccountID);
        store.put(nodeAccount
                .copyBuilder()
                .tinybarBalance(nodeAccount.tinybarBalance() + amount)
                .build());
    }

    /**
     * Pays the staking reward account the given amount. If the staking reward account doesn't exist, an exception is
     * thrown. This account *should* have been created at genesis, so it should always exist, even if staking rewards
     * are disabled.
     *
     * @param amount The amount to credit the staking reward account.
     * @throws IllegalStateException if the staking rewards account doesn't exist
     */
    private void payStakingRewardAccount(final long amount) {
        if (amount == 0) return;
        final var stakingAccount = lookupAccount("Staking reward", stakingRewardAccountID);
        store.put(stakingAccount
                .copyBuilder()
                .tinybarBalance(stakingAccount.tinybarBalance() + amount)
                .build());
    }

    /**
     * Looks up and returns the {@link Account} with the given ID. If the account doesn't exist, an exception is thrown.
     *
     * @param logName The name of this account to use in log statements.
     * @param id The account ID to lookup
     * @return The looked up account.
     * @throws IllegalStateException if the given account doesn't exist
     */
    @NonNull
    private Account lookupAccount(String logName, AccountID id) {
        var account = store.get(id);
        if (account == null) {
            logger.fatal("{} account {} does not exist", logName, id);
            throw new IllegalStateException(logName + " account does not exist");
        }
        return account;
    }
}
