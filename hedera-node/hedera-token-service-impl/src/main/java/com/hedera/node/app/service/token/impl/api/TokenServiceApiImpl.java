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

package com.hedera.node.app.service.token.impl.api;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_HAS_PENDING_AIRDROPS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hedera.node.app.service.token.api.TokenServiceApi.FreeAliasOnDeletion.YES;
import static com.hedera.node.app.spi.key.KeyUtils.IMMUTABILITY_SENTINEL_KEY;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.hapi.utils.EntityType;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.ContractChangeSummary;
import com.hedera.node.app.service.token.api.FeeStreamBuilder;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.validators.StakingValidator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.ids.WritableEntityCounters;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.record.DeleteCapableTransactionStreamBuilder;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements {@link TokenServiceApi} via {@link WritableAccountStore} calls.
 */
public class TokenServiceApiImpl implements TokenServiceApi {
    private static final Logger logger = LogManager.getLogger(TokenServiceApiImpl.class);

    private final WritableAccountStore accountStore;
    private final AccountID fundingAccountID;
    private final AccountID stakingRewardAccountID;
    private final AccountID nodeRewardAccountID;
    private final StakingConfig stakingConfig;
    private final Predicate<CryptoTransferTransactionBody> customFeeTest;

    /**
     * Constructs a {@link TokenServiceApiImpl}.
     *
     * @param config         the configuration
     * @param writableStates the writable states
     * @param customFeeTest  a predicate for determining if a transfer has custom fees
     * @param entityCounters
     */
    public TokenServiceApiImpl(
            @NonNull final Configuration config,
            @NonNull final WritableStates writableStates,
            @NonNull final Predicate<CryptoTransferTransactionBody> customFeeTest,
            @NonNull final WritableEntityCounters entityCounters) {
        this.customFeeTest = customFeeTest;
        requireNonNull(config);
        this.accountStore = new WritableAccountStore(writableStates, entityCounters);

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
    public void assertValidStakingElectionForCreation(
            final boolean isStakingEnabled,
            final boolean hasDeclineRewardChange,
            @NonNull final String stakedIdKind,
            @Nullable final AccountID stakedAccountIdInOp,
            @Nullable final Long stakedNodeIdInOp,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final NetworkInfo networkInfo) {
        StakingValidator.validateStakedIdForCreation(
                isStakingEnabled,
                hasDeclineRewardChange,
                stakedIdKind,
                stakedAccountIdInOp,
                stakedNodeIdInOp,
                accountStore,
                networkInfo);
    }

    @Override
    public void assertValidStakingElectionForUpdate(
            final boolean isStakingEnabled,
            final boolean hasDeclineRewardChange,
            @NonNull final String stakedIdKind,
            @Nullable final AccountID stakedAccountIdInOp,
            @Nullable final Long stakedNodeIdInOp,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final NetworkInfo networkInfo) {
        StakingValidator.validateStakedIdForUpdate(
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
    public void markAsContract(@NonNull final AccountID accountId, @Nullable AccountID autoRenewAccountId) {
        requireNonNull(accountId);
        final var accountAsContract = requireNonNull(accountStore.get(accountId))
                .copyBuilder()
                .smartContract(true)
                .autoRenewAccountId(autoRenewAccountId)
                .build();
        accountStore.put(accountAsContract);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finalizeHollowAccountAsContract(@NonNull final AccountID hollowAccountId) {
        requireNonNull(hollowAccountId);
        final var hollowAccount = requireNonNull(accountStore.get(hollowAccountId));
        if (!IMMUTABILITY_SENTINEL_KEY.equals(hollowAccount.keyOrThrow())) {
            throw new IllegalArgumentException(
                    "Cannot finalize non-hollow account " + hollowAccountId + " as contract");
        }
        final var accountAsContract = hollowAccount
                .copyBuilder()
                .key(Key.newBuilder()
                        .contractID(ContractID.newBuilder().contractNum(hollowAccountId.accountNumOrThrow()))
                        .build())
                .smartContract(true)
                .maxAutoAssociations(hollowAccount.numberAssociations())
                .build();
        accountStore.put(accountAsContract);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteContract(@NonNull final ContractID contractId) {
        requireNonNull(contractId);

        // If the contractId cannot find a contract, then we have nothing to do here. But that would be an error
        // condition -- it really should never happen.
        final var contract = accountStore.getContractById(contractId);
        if (contract == null) {
            logger.warn("Contract {} does not exist, so cannot be deleted", contractId);
            return;
        }

        // It may be that the contract exists, but has already been deleted. In that case, there really shouldn't
        // be anything to do here. But we'll log a warning just in case, because it would indicate a very probable
        // bug somewhere. And we'll go ahead and do the cleanup anyway.
        if (contract.deleted()) {
            logger.warn("Trying to delete Contract {}, which is already deleted", contractId);
        }

        // The contract account may or may not have an alias on it. Normally they do, but if they are created using
        // the HAPI ContractCreate, they don't necessarily have an alias (the user has to choose to do so). This means
        // If there is an alias, then we need to remove it from the account store, and we need to remove the alias
        // from the contract account.
        final var evmAddress = contract.alias();
        accountStore.removeAlias(evmAddress);
        accountStore.put(contract.copyBuilder().alias(Bytes.EMPTY).deleted(true).build());

        // It may be (but should never happen) that the alias in the given contractId does not match the alias on the
        // contract account itself. This shouldn't happen because it means that somehow we were able to look up the
        // contract from the store using alias A, but then the contract we got back had alias B. Since the alias
        // cannot be changed once set, this shouldn't be possible. We will log an error and remove the alias in the
        // contract ID from the store.
        final var usedEvmAddress = contractId.evmAddressOrElse(Bytes.EMPTY);
        if (!usedEvmAddress.equals(evmAddress)) {
            logger.error(
                    "Contract {} has an alias {} different than its referencing EVM address {}",
                    contractId,
                    evmAddress,
                    usedEvmAddress);
            accountStore.removeAlias(usedEvmAddress);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementParentNonce(@NonNull final ContractID parentId) {
        requireNonNull(parentId);
        final var contract = requireNonNull(accountStore.getContractById(parentId));
        accountStore.put(contract.copyBuilder()
                .ethereumNonce(contract.ethereumNonce() + 1)
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementSenderNonce(@NonNull final AccountID senderId) {
        requireNonNull(senderId);
        final var sender = requireNonNull(accountStore.get(senderId));
        accountStore.put(
                sender.copyBuilder().ethereumNonce(sender.ethereumNonce() + 1).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNonce(@NonNull final AccountID accountId, final long nonce) {
        requireNonNull(accountId);
        final var target = requireNonNull(accountStore.get(accountId));
        accountStore.put(target.copyBuilder().ethereumNonce(nonce).build());
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
        final var from = requireNonNull(accountStore.get(fromId));
        final var to = requireNonNull(accountStore.get(toId));
        if (from.tinybarBalance() < amount) {
            throw new IllegalArgumentException(
                    "Insufficient balance to transfer " + amount + " tinybars from " + fromId + " to " + toId);
        }
        if (to.tinybarBalance() + amount < 0) {
            throw new IllegalArgumentException(
                    "Overflow on transfer of " + amount + " tinybars from " + fromId + " to " + toId);
        }
        if (!from.accountIdOrThrow().equals(to.accountIdOrThrow())) {
            accountStore.put(from.copyBuilder()
                    .tinybarBalance(from.tinybarBalance() - amount)
                    .build());
            accountStore.put(to.copyBuilder()
                    .tinybarBalance(to.tinybarBalance() + amount)
                    .build());
        }
    }

    @Override
    public ContractChangeSummary summarizeContractChanges() {
        return accountStore.summarizeContractChanges();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateStorageMetadata(
            @NonNull final ContractID contractID, @NonNull final Bytes firstKey, final int netChangeInSlotsUsed) {
        requireNonNull(firstKey);
        requireNonNull(contractID);
        final var target = accountStore.getContractById(contractID);
        if (target == null) {
            throw new IllegalArgumentException("No contract found for ID " + contractID);
        }
        final var newNumKvPairs = target.contractKvPairsNumber() + netChangeInSlotsUsed;
        if (newNumKvPairs < 0) {
            throw new IllegalArgumentException("Cannot change # of storage slots (currently "
                    + target.contractKvPairsNumber()
                    + ") by "
                    + netChangeInSlotsUsed
                    + " for contract "
                    + contractID);
        }
        accountStore.put(target.copyBuilder()
                .firstContractStorageKey(firstKey)
                .contractKvPairsNumber(newNumKvPairs)
                .build());
    }

    @Override
    public boolean chargeNetworkFee(
            @NonNull final AccountID payerId, final long amount, @NonNull final FeeStreamBuilder rb) {
        requireNonNull(rb);
        requireNonNull(payerId);

        final var payerAccount = lookupAccount("Payer", payerId);
        final var amountToCharge = Math.min(amount, payerAccount.tinybarBalance());
        chargePayer(payerAccount, amountToCharge);
        // We may be charging for preceding child record fees, which are additive to the base fee
        rb.transactionFee(rb.transactionFee() + amountToCharge);
        distributeToNetworkFundingAccounts(amountToCharge, rb);
        return amountToCharge == amount;
    }

    @Override
    public void chargeFees(
            @NonNull AccountID payerId,
            AccountID nodeAccountId,
            @NonNull Fees fees,
            @NonNull final FeeStreamBuilder rb) {
        requireNonNull(rb);
        requireNonNull(fees);
        requireNonNull(payerId);
        requireNonNull(nodeAccountId);

        // Note: these four accounts (payer, funding, staking reward, node reward) MUST exist for the transaction to be
        // valid and for fees to be processed. If any of them do not exist, the entire transaction will fail. There is
        // no conceivable way that these accounts *should* be null at this point.
        final var payerAccount = lookupAccount("Payer", payerId);
        if (payerAccount.tinybarBalance() < fees.networkFee()) {
            throw new IllegalArgumentException(("Payer %s (balance=%d) cannot afford network fee of %d, "
                            + "which should have been a due diligence failure")
                    .formatted(payerId, payerAccount.tinybarBalance(), fees.networkFee()));
        }
        if (fees.serviceFee() > 0 && payerAccount.tinybarBalance() < fees.totalFee()) {
            throw new IllegalArgumentException(("Payer %s (balance=%d) cannot afford total fee of %d, "
                            + "which means service component should have been zeroed out")
                    .formatted(payerId, payerAccount.tinybarBalance(), fees.totalFee()));
        }
        // Prioritize network fee over node fee
        final long chargeableNodeFee = Math.min(fees.nodeFee(), payerAccount.tinybarBalance() - fees.networkFee());
        final long amountToCharge = fees.totalWithoutNodeFee() + chargeableNodeFee;
        final long amountToDistributeToFundingAccounts = amountToCharge - chargeableNodeFee;

        chargePayer(payerAccount, amountToCharge);
        // Record the amount charged into the record builder
        rb.transactionFee(amountToCharge);
        distributeToNetworkFundingAccounts(amountToDistributeToFundingAccounts, rb);

        if (chargeableNodeFee > 0) {
            final var nodeAccount = lookupAccount("Node account", nodeAccountId);
            accountStore.put(nodeAccount
                    .copyBuilder()
                    .tinybarBalance(nodeAccount.tinybarBalance() + chargeableNodeFee)
                    .build());
        }
    }

    @Override
    public void refundFees(@NonNull AccountID receiver, @NonNull Fees fees, @NonNull final FeeStreamBuilder rb) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public long originalKvUsageFor(@NonNull final ContractID id) {
        Account account = accountStore.getContractById(id);
        final var oldAccount = account == null ? null : accountStore.getOriginalValue(account.accountId());
        return oldAccount == null ? 0 : oldAccount.contractKvPairsNumber();
    }

    @Override
    public void updateContract(Account contract) {
        accountStore.put(contract);
    }

    /**
     * A utility method that charges (debits) the payer up to the given total fee. If the payer account doesn't exist,
     * then an exception is thrown.
     *
     * @param payerAccount the account to charge
     * @param amount the maximum amount to charge
     * @throws IllegalStateException if the payer account doesn't exist
     */
    private void chargePayer(@NonNull final Account payerAccount, final long amount) {
        if (amount > payerAccount.tinybarBalance()) {
            throw new IllegalArgumentException("Payer %s (balance=%d) cannot afford fee of %d"
                    .formatted(payerAccount, payerAccount.tinybarBalance(), amount));
        }
        final long currentBalance = payerAccount.tinybarBalance();
        accountStore.put(payerAccount
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
        accountStore.put(nodeAccount
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
        accountStore.put(stakingAccount
                .copyBuilder()
                .tinybarBalance(stakingAccount.tinybarBalance() + amount)
                .build());
    }

    /**
     * Looks up and returns the {@link Account} with the given ID. If the account doesn't exist, an exception is thrown.
     *
     * @param logName The name of this account to use in log statements.
     * @param id The account ID to lookup
     * @return The looked up account
     * @throws IllegalStateException if the given account doesn't exist
     */
    @NonNull
    private Account lookupAccount(String logName, AccountID id) {
        var account = accountStore.get(id);
        if (account == null) {
            throw new IllegalStateException(logName + " account " + id + " does not exist");
        }
        return account;
    }

    private record InvolvedAccounts(@NonNull Account deletedAccount, @NonNull Account obtainerAccount) {
        private InvolvedAccounts {
            requireNonNull(deletedAccount);
            requireNonNull(obtainerAccount);
        }
    }

    @Override
    public boolean checkForCustomFees(@NonNull final CryptoTransferTransactionBody op) {
        return customFeeTest.test(op);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAndTransfer(
            @NonNull final AccountID deletedId,
            @NonNull final AccountID obtainerId,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final DeleteCapableTransactionStreamBuilder recordBuilder,
            @NonNull final FreeAliasOnDeletion freeAliasOnDeletion) {
        // validate the semantics involving dynamic properties and state.
        // Gets delete and transfer accounts from state
        final var deleteAndTransferAccounts = validateSemantics(deletedId, obtainerId, expiryValidator);
        transferRemainingBalance(expiryValidator, deleteAndTransferAccounts);

        // get the account from account store that has all balance changes
        // commit the account with deleted flag set to true
        final var updatedDeleteAccount = requireNonNull(accountStore.getForModify(deletedId));
        final var builder = updatedDeleteAccount.copyBuilder().deleted(true);
        if (freeAliasOnDeletion == YES) {
            accountStore.removeAlias(updatedDeleteAccount.alias());
            builder.alias(Bytes.EMPTY);
        }
        accountStore.put(builder.build());

        // add the transfer account for this deleted account to record builder.
        // This is needed while computing staking rewards. In the future it will also be added
        // to the transaction record exported to mirror node.
        recordBuilder.addBeneficiaryForDeletedAccount(deletedId, obtainerId);
    }

    private InvolvedAccounts validateSemantics(
            @NonNull final AccountID deletedId,
            @NonNull final AccountID obtainerId,
            @NonNull final ExpiryValidator expiryValidator) {
        // validate if accounts exist
        final var deletedAccount = accountStore.get(deletedId);
        validateTrue(deletedAccount != null, INVALID_ACCOUNT_ID);
        validateFalse(deletedAccount.hasHeadPendingAirdropId(), ACCOUNT_HAS_PENDING_AIRDROPS);
        final var transferAccount = accountStore.get(obtainerId);
        validateTrue(transferAccount != null, INVALID_TRANSFER_ACCOUNT_ID);
        // if the account is treasury for any other token, it can't be deleted
        validateFalse(deletedAccount.numberTreasuryTitles() > 0, ACCOUNT_IS_TREASURY);
        // checks if accounts are detached
        final var isExpired = areAccountsDetached(deletedAccount, transferAccount, expiryValidator);
        validateFalse(isExpired, ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        // An account can't be deleted if there are any tokens associated with this account
        validateTrue(deletedAccount.numberPositiveBalances() == 0, TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
        return new InvolvedAccounts(deletedAccount, transferAccount);
    }

    private void transferRemainingBalance(
            @NonNull final ExpiryValidator expiryValidator, @NonNull final InvolvedAccounts involvedAccounts) {
        final var fromAccount = involvedAccounts.deletedAccount();
        final var toAccount = involvedAccounts.obtainerAccount();
        final long newFromBalance = computeNewBalance(expiryValidator, fromAccount, -1 * fromAccount.tinybarBalance());
        final long newToBalance = computeNewBalance(expiryValidator, toAccount, fromAccount.tinybarBalance());
        accountStore.put(
                fromAccount.copyBuilder().tinybarBalance(newFromBalance).build());
        accountStore.put(toAccount.copyBuilder().tinybarBalance(newToBalance).build());
    }

    private long computeNewBalance(
            final ExpiryValidator expiryValidator, final Account account, final long adjustment) {
        validateTrue(!account.deleted(), ACCOUNT_DELETED);
        validateTrue(
                !expiryValidator.isDetached(
                        EntityType.ACCOUNT, account.expiredAndPendingRemoval(), account.tinybarBalance()),
                ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        final long balance = account.tinybarBalance();
        validateTrue(balance + adjustment >= 0, INSUFFICIENT_ACCOUNT_BALANCE);
        return balance + adjustment;
    }

    private boolean areAccountsDetached(
            @NonNull Account deleteAccount,
            @NonNull Account transferAccount,
            @NonNull final ExpiryValidator expiryValidator) {
        return expiryValidator.isDetached(
                        getEntityType(deleteAccount),
                        deleteAccount.expiredAndPendingRemoval(),
                        deleteAccount.tinybarBalance())
                || expiryValidator.isDetached(
                        getEntityType(transferAccount),
                        transferAccount.expiredAndPendingRemoval(),
                        transferAccount.tinybarBalance());
    }

    private EntityType getEntityType(@NonNull final Account account) {
        return account.smartContract() ? EntityType.CONTRACT_BYTECODE : EntityType.ACCOUNT;
    }

    private void distributeToNetworkFundingAccounts(final long amount, @NonNull final FeeStreamBuilder rb) {
        // We may have a rounding error, so we will first remove the node and staking rewards from the total, and then
        // whatever is left over goes to the funding account.
        long balance = amount;

        // We only pay node and staking rewards if the feature is enabled
        if (stakingConfig.isEnabled()) {
            final long nodeReward = (stakingConfig.feesNodeRewardPercentage() * amount) / 100;
            balance -= nodeReward;
            payNodeRewardAccount(nodeReward);

            final long stakingReward = (stakingConfig.feesStakingRewardPercentage() * amount) / 100;
            balance -= stakingReward;
            payStakingRewardAccount(stakingReward);
        }

        // Whatever is left over goes to the funding account
        final var fundingAccount = lookupAccount("Funding", fundingAccountID);
        accountStore.put(fundingAccount
                .copyBuilder()
                .tinybarBalance(fundingAccount.tinybarBalance() + balance)
                .build());
    }
}
