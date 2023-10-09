/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hedera.node.app.hapi.fees.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.node.app.hapi.fees.usage.crypto.CryptoOpsUsage.CREATE_SLOT_MULTIPLIER;
import static com.hedera.node.app.hapi.fees.usage.crypto.entities.CryptoEntitySizes.CRYPTO_ENTITY_SIZES;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BOOL_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.INT_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.LONG_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.getAccountKeyStorageSize;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.validators.CryptoCreateValidator;
import com.hedera.node.app.service.token.impl.validators.StakingValidator;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.CryptoCreateWithAliasConfig;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_CREATE}.
 */
@Singleton
public class CryptoCreateHandler extends BaseCryptoHandler implements TransactionHandler {
    private final CryptoCreateValidator cryptoCreateValidator;

    private StakingValidator stakingValidator;

    @Inject
    public CryptoCreateHandler(
            @NonNull final CryptoCreateValidator cryptoCreateValidator,
            @NonNull final StakingValidator stakingValidator) {
        this.cryptoCreateValidator =
                requireNonNull(cryptoCreateValidator, "The supplied argument 'cryptoCreateValidator' must not be null");
        this.stakingValidator =
                requireNonNull(stakingValidator, "The supplied argument 'stakingValidator' must not be null");
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().cryptoCreateAccountOrThrow();
        pureChecks(context.body());

        if (op.hasKey()) {
            final var key = op.key();
            if (op.alias() != null && !op.alias().equals(Bytes.EMPTY)) {
                final var alias = op.alias();
                // add evm address key to req keys only if it is derived from a key, diff than the admin key
                final var isAliasDerivedFromDiffKey = !(key.hasEcdsaSecp256k1()
                        && Arrays.equals(
                                recoverAddressFromPubKey(key.ecdsaSecp256k1().toByteArray()), alias.toByteArray()));
                if (isAliasDerivedFromDiffKey) {
                    context.requireSignatureForHollowAccountCreation(alias);
                }
            }

            final var receiverSigReq = op.receiverSigRequired();
            if (receiverSigReq) {
                context.requireKey(op.keyOrThrow());
            }
        }
    }

    /**
     * This method is called during the handle workflow. It executes the {@code CryptoCreate}
     * transaction, creating a new account with the given properties.
     * If the transaction is successful, the account is created and the payer account is charged
     * the transaction fee and the initial balance of new account and the balance of the
     * new account is set to the initial balance.
     * If the transaction is not successful, the account is not created and the payer account is
     * charged the transaction fee.
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws HandleException      if the transaction is not successful due to payer
     * account being deleted or has insufficient balance or the account is not created due to
     * the usage of a price regime
     */
    @Override
    public void handle(@NonNull final HandleContext handleContext) {
        requireNonNull(handleContext);
        final var txnBody = handleContext.body();
        final var op = txnBody.cryptoCreateAccount();
        final var accountStore = handleContext.writableStore(WritableAccountStore.class);

        // FUTURE: Use the config and check if accounts can be created.
        //  Currently, this check is being done in `finishCryptoCreate` before `commit`

        // validate fields in the transaction body that involves checking with
        // dynamic properties or state
        validateSemantics(handleContext, accountStore, op);

        if (op.initialBalance() > 0) {
            final var payer = accountStore.getAccountById(
                    handleContext.body().transactionIDOrThrow().accountIDOrThrow());
            final long newPayerBalance = payer.tinybarBalance() - op.initialBalance();
            // Change payer's balance to reflect the deduction of the initial balance for the new
            // account
            final var modifiedPayer =
                    payer.copyBuilder().tinybarBalance(newPayerBalance).build();
            accountStore.put(modifiedPayer);
        }

        // Build the new account to be persisted based on the transaction body
        final var accountCreated = buildAccount(op, handleContext);
        accountStore.put(accountCreated);

        // set newly created account number in the record builder
        final var createdAccountID = accountCreated.accountId();
        final var recordBuilder = handleContext.recordBuilder(CryptoCreateRecordBuilder.class);
        recordBuilder.accountID(createdAccountID);

        // put if any new alias is associated with the account into account store
        if (op.alias() != Bytes.EMPTY) {
            accountStore.putAlias(op.alias(), createdAccountID);
        }
    }

    /* ----------- Helper Methods ----------- */

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.cryptoCreateAccountOrThrow();
        validateTruePreCheck(op.initialBalance() >= 0L, INVALID_INITIAL_BALANCE);
        validateTruePreCheck(op.hasAutoRenewPeriod(), INVALID_RENEWAL_PERIOD);
        validateTruePreCheck(
                op.sendRecordThreshold() >= 0L, INVALID_SEND_RECORD_THRESHOLD); // FUTURE: should this return
        // SEND_RECORD_THRESHOLD_FIELD_IS_DEPRECATED
        validateTruePreCheck(
                op.receiveRecordThreshold() >= 0L, INVALID_RECEIVE_RECORD_THRESHOLD); // FUTURE: should this return
        // RECEIVE_RECORD_THRESHOLD_FIELD_IS_DEPRECATED
        validateTruePreCheck(
                !op.hasProxyAccountID()
                        || (op.hasProxyAccountID() && op.proxyAccountID().equals(AccountID.DEFAULT)),
                PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED);
    }

    /**
     * Validate the fields in the transaction body that involves checking with dynamic
     * properties or state. This check is done as part of the handle workflow.
     * @param context handle context
     * @param accountStore account store
     * @param op crypto create transaction body
     * @return the payer account if validated successfully
     */
    private void validateSemantics(
            @NonNull final HandleContext context,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final CryptoCreateTransactionBody op) {
        final var cryptoCreateWithAliasConfig =
                context.configuration().getConfigData(CryptoCreateWithAliasConfig.class);
        final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
        final var entitiesConfig = context.configuration().getConfigData(EntitiesConfig.class);
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        final var accountConfig = context.configuration().getConfigData(AccountsConfig.class);

        if (accountStore.getNumberOfAccounts() + 1 > accountConfig.maxNumber()) {
            throw new HandleException(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
        }

        if (op.initialBalance() > 0) {
            // validate payer account exists and has enough balance
            final var payer = getIfUsable(
                    context.body().transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT),
                    accountStore,
                    context.expiryValidator(),
                    INVALID_PAYER_ACCOUNT_ID);
            final long newPayerBalance = payer.tinybarBalance() - op.initialBalance();
            validatePayer(payer, newPayerBalance);
        }

        context.attributeValidator().validateMemo(op.memo());
        // If the body has no transaction id, this is an internal dispatch, and we
        // should allow the empty key list in case of a hollow account creation
        cryptoCreateValidator.validateKeyAliasAndEvmAddressCombinations(
                op,
                context.attributeValidator(),
                cryptoCreateWithAliasConfig,
                accountStore,
                !context.body().hasTransactionID());
        context.attributeValidator()
                .validateAutoRenewPeriod(op.autoRenewPeriod().seconds());
        validateFalse(
                cryptoCreateValidator.tooManyAutoAssociations(
                        op.maxAutomaticTokenAssociations(), ledgerConfig, entitiesConfig, tokensConfig),
                REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);
        validateFalse(
                op.hasProxyAccountID() && !op.proxyAccountID().equals(AccountID.DEFAULT),
                PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED);
        if (op.hasStakedAccountId() || op.hasStakedNodeId()) {
            stakingValidator.validateStakedIdForCreation(
                    context.configuration().getConfigData(StakingConfig.class).isEnabled(),
                    op.declineReward(),
                    op.stakedId().kind().name(),
                    op.stakedAccountId(),
                    op.stakedNodeId(),
                    accountStore,
                    context.networkInfo());
        }
    }

    /**
     * Validates the payer account exists and has enough balance to cover the initial balance of the
     * account to be created.
     *
     * @param payer the payer account
     * @param newPayerBalance the initial balance of the account to be created
     */
    private void validatePayer(@NonNull final Account payer, final long newPayerBalance) {
        // If the payer account is deleted, throw an exception
        if (payer.deleted()) {
            throw new HandleException(ACCOUNT_DELETED);
        }
        if (newPayerBalance < 0) {
            throw new HandleException(INSUFFICIENT_PAYER_BALANCE);
        }
        // FUTURE: check if payer account is detached when we have started expiring accounts ?
    }

    /**
     * Builds an account based on the transaction body and the consensus time.
     *
     * @param op the transaction body
     * @param handleContext the handle context
     * @return the account created
     */
    private Account buildAccount(CryptoCreateTransactionBody op, HandleContext handleContext) {
        long autoRenewPeriod = op.autoRenewPeriodOrThrow().seconds();
        long consensusTime = handleContext.consensusNow().getEpochSecond();
        long expiry = consensusTime + autoRenewPeriod;
        var builder = Account.newBuilder()
                .memo(op.memo())
                .expirationSecond(expiry)
                .autoRenewSeconds(autoRenewPeriod)
                .receiverSigRequired(op.receiverSigRequired())
                .maxAutoAssociations(op.maxAutomaticTokenAssociations())
                .tinybarBalance(op.initialBalance())
                .declineReward(op.declineReward());

        if (onlyKeyProvided(op)) {
            builder.key(op.keyOrThrow());
        } else if (keyAndAliasProvided(op)) {
            builder.key(op.keyOrThrow()).alias(op.alias());
        }

        if (op.hasStakedAccountId()) {
            builder.stakedAccountId(op.stakedAccountId());
        } else if (op.hasStakedNodeId()) {
            builder.stakedNodeId(op.stakedNodeId());
        }

        // set the new account number
        final var hederaConfig = handleContext.configuration().getConfigData(HederaConfig.class);
        builder.accountId(AccountID.newBuilder()
                .accountNum(handleContext.newEntityNum())
                .realmNum(hederaConfig.realm())
                .shardNum(hederaConfig.shard())
                .build());
        return builder.build();
    }

    /**
     * Checks if only key is provided.
     *
     * @param op the transaction body
     * @return true if only key is provided, false otherwise
     */
    private boolean onlyKeyProvided(@NonNull final CryptoCreateTransactionBody op) {
        return op.hasKey() && op.alias().equals(Bytes.EMPTY);
    }

    /**
     * Checks if both key and alias are provided.
     *
     * @param op the transaction body
     * @return true if both key and alias are provided, false otherwise
     */
    private boolean keyAndAliasProvided(@NonNull final CryptoCreateTransactionBody op) {
        return op.hasKey() && !op.alias().equals(Bytes.EMPTY);
    }

    @Override
    @NonNull
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        // Variable bytes plus two additional longs for balance and auto-renew period; plus a boolean for receiver sig
        // required.
        final var op = feeContext.body().cryptoCreateAccountOrThrow();
        return cryptoCreateFees(op, feeContext.feeCalculator(SubType.DEFAULT));
    }

    public static Fees cryptoCreateFees(final CryptoCreateTransactionBody op, final FeeCalculator feeCalculator) {
        final var keySize = op.hasKey() ? getAccountKeyStorageSize(fromPbj(op.keyOrThrow())) : 0L;
        final var baseSize = op.memo().length() + keySize + (op.maxAutomaticTokenAssociations() > 0 ? INT_SIZE : 0L);
        final var lifeTime = op.autoRenewPeriodOrElse(Duration.DEFAULT).seconds();
        return feeCalculator
                .addBytesPerTransaction(baseSize + 2 * LONG_SIZE + BOOL_SIZE)
                .addRamByteSeconds((CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr() + baseSize) * lifeTime)
                .addRamByteSeconds(op.maxAutomaticTokenAssociations() * lifeTime * CREATE_SLOT_MULTIPLIER)
                .addNetworkRamByteSeconds(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs())
                .calculate();
    }
}
