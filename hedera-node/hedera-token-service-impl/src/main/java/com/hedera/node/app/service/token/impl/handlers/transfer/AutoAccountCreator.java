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

import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.node.app.service.mono.txns.crypto.AbstractAutoCreationLogic.AUTO_MEMO;
import static com.hedera.node.app.service.mono.txns.crypto.AbstractAutoCreationLogic.LAZY_MEMO;
import static com.hedera.node.app.service.mono.txns.crypto.AbstractAutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static com.hedera.node.app.service.token.impl.handlers.transfer.AliasUtils.asKeyFromAlias;
import static com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl.isOfEvmAddressSize;
import static com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator.IMMUTABILITY_SENTINEL_KEY;
import static com.hedera.node.app.spi.key.KeyUtils.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.swirlds.common.utility.CommonUtils.hex;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutoAccountCreator {
    private static final Logger log = LogManager.getLogger(AutoAccountCreator.class);
    private WritableAccountStore accountStore;
    private HandleContext handleContext;
    // checks tokenAliasMap if the change consists an alias that is already used in previous
    // iteration of the token transfer list. This map is used to count number of
    // maxAutoAssociations needed on auto created account
    protected final Map<ProtoBytes, Set<TokenID>> tokenAliasMap = new HashMap<>();
    private static final CryptoUpdateTransactionBody.Builder UPDATE_TXN_BODY_BUILDER =
            CryptoUpdateTransactionBody.newBuilder()
                    .key(Key.newBuilder().ecdsaSecp256k1(Bytes.EMPTY).build());

    public AutoAccountCreator(@NonNull final HandleContext handleContext) {
        this.handleContext = requireNonNull(handleContext);
        this.accountStore = handleContext.writableStore(WritableAccountStore.class);
    }

    /**
     * Creates an account for the given alias.
     *
     * @param alias                  the alias to create the account for
     * @param maxAutoAssociations   the maxAutoAssociations to set on the account
     */
    public AccountID create(@NonNull final Bytes alias, int maxAutoAssociations) {
        requireNonNull(alias);

        final var accountsConfig = handleContext.configuration().getConfigData(AccountsConfig.class);

        validateTrue(
                accountStore.sizeOfAccountState() + 1 <= accountsConfig.maxNumber(),
                ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);

        final TransactionBody.Builder syntheticCreation;
        String memo;
        byte[] evmAddress = null;

        final var isAliasEVMAddress = EntityIdUtils.isOfEvmAddressSize(alias);
        if (isAliasEVMAddress) {
            syntheticCreation = createHollowAccount(alias, 0L, maxAutoAssociations);
            memo = LAZY_MEMO;
        } else {
            final var key = asKeyFromAlias(alias);
            validateTrue(isValid(key), INVALID_ALIAS_KEY);
            if (key.hasEcdsaSecp256k1()) {
                evmAddress = tryAddressRecovery(key, EthSigsUtils::recoverAddressFromPubKey);
                syntheticCreation = createAccount(Bytes.wrap(evmAddress), key, 0L, maxAutoAssociations);
            } else {
                syntheticCreation = createAccount(alias, key, 0L, maxAutoAssociations);
            }
            memo = AUTO_MEMO;
        }

        // TODO : distribute autocreation fee and deduct payer balance
        //        final var payer = handleContext.body().transactionID().accountID();
        //        final var payerAccount = accountStore.get(payer);
        //        final var currentBalance = payerAccount.tinybarBalance();
        //        validateTrue(currentBalance >= fee, INSUFFICIENT_PAYER_BALANCE);
        //        final var payerCopy = payerAccount.copyBuilder()
        //                .tinybarBalance(currentBalance - fee)
        //                .build();
        //        accountStore.put(payerCopy.copyBuilder().build());

        // TODO: Check if this is the correct verifier
        final Predicate<Key> verifier =
                key -> handleContext.verificationFor(key).passed();

        final var childRecord = handleContext.dispatchRemovableChildTransaction(
                syntheticCreation.memo(memo).build(), CryptoCreateRecordBuilder.class, verifier, handleContext.payer());

        if (!isAliasEVMAddress && evmAddress != null) {
            childRecord.evmAddress(Bytes.wrap(evmAddress));
        }

        var fee = autoCreationFeeFor(syntheticCreation);
        if (isAliasEVMAddress) {
            fee += getLazyCreationFinalizationFee();
        }
        childRecord.transactionFee(fee);

        final var createdAccountId =
                accountStore.getAccountIDByAlias(evmAddress == null ? alias : Bytes.wrap(evmAddress));
        validateTrue(createdAccountId != null, FAIL_INVALID);
        return createdAccountId;
    }

    /**
     * Get fees for finalization of lazy creation.
     * @return fee for finalization of lazy creation
     */
    private long getLazyCreationFinalizationFee() {
        return autoCreationFeeFor(TransactionBody.newBuilder().cryptoUpdateAccount(UPDATE_TXN_BODY_BUILDER));
    }

    /**
     * Get fees for auto creation.
     * @param syntheticCreation transaction body for auto creation
     * @return fee for auto creation
     */
    private long autoCreationFeeFor(@NonNull final TransactionBody.Builder syntheticCreation) {
        final var topLevelPayer = handleContext.payer();
        final var payerAccount = accountStore.get(topLevelPayer);
        validateTrue(payerAccount != null, PAYER_ACCOUNT_NOT_FOUND);
        final var txn = Transaction.newBuilder().body(syntheticCreation.build()).build();
        //        final var fees = handleContext.feeCalculator().computePayment(txn, payerAccount.key());
        //        return fees.serviceFee() + fees.networkFee() + fees.nodeFee();
        // TODO : need to use fee calculator
        return 100;
    }

    /**
     * Create a transaction body for new hollow-account with the given alias.
     * @param alias alias of the account
     * @param balance initial balance of the account
     * @param maxAutoAssociations maxAutoAssociations of the account
     * @return transaction body for new hollow-account
     */
    public TransactionBody.Builder createHollowAccount(
            @NonNull final Bytes alias, final long balance, final int maxAutoAssociations) {
        final var baseBuilder = createAccountBase(balance, maxAutoAssociations);
        baseBuilder.key(IMMUTABILITY_SENTINEL_KEY).alias(alias).memo(LAZY_MEMO);
        return TransactionBody.newBuilder().cryptoCreateAccount(baseBuilder.build());
    }

    /**
     * Create a transaction body for new account with the given balance and other common fields.
     * @param balance initial balance of the account
     * @param maxAutoAssociations maxAutoAssociations of the account
     * @return transaction body for new account
     */
    private CryptoCreateTransactionBody.Builder createAccountBase(final long balance, final int maxAutoAssociations) {
        return CryptoCreateTransactionBody.newBuilder()
                .initialBalance(balance)
                .maxAutomaticTokenAssociations(maxAutoAssociations)
                .autoRenewPeriod(Duration.newBuilder().seconds(THREE_MONTHS_IN_SECONDS));
    }

    /**
     * Create a transaction body for new account with the given alias, key, balance and maxAutoAssociations.
     * @param alias alias of the account
     * @param key key of the account
     * @param balance initial balance of the account
     * @param maxAutoAssociations maxAutoAssociations of the account
     * @return transaction body for new account
     */
    private TransactionBody.Builder createAccount(
            @NonNull final Bytes alias, @NonNull final Key key, final long balance, final int maxAutoAssociations) {
        final var baseBuilder = createAccountBase(balance, maxAutoAssociations);
        baseBuilder.key(key).alias(alias).memo(AUTO_MEMO);
        return TransactionBody.newBuilder().cryptoCreateAccount(baseBuilder.build());
    }

    /**
     * Try to recover EVM address from the given key.
     * @param key key to recover EVM address from
     * @param addressRecovery function to recover EVM address from the given key
     * @return recovered EVM address if successful, otherwise null
     */
    @Nullable
    private byte[] tryAddressRecovery(@Nullable final Key key, final UnaryOperator<byte[]> addressRecovery) {
        if (key != null && key.hasEcdsaSecp256k1()) {
            // Only compressed keys are stored at the moment
            final var keyBytes = key.ecdsaSecp256k1OrThrow();
            if (keyBytes.length() == ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH) {
                final var keyBytesArray = keyBytes.toByteArray();
                final var evmAddress = addressRecovery.apply(keyBytesArray);
                if (isEvmAddress(Bytes.wrap(evmAddress))) {
                    return evmAddress;
                } else {
                    // Not ever expected, since above checks should imply a valid input to the
                    // LibSecp256k1 library
                    log.warn("Unable to recover EVM address from {}", () -> hex(keyBytesArray));
                }
            }
        }
        return null;
    }

    /**
     * Check if the given address is of a valid EVM address length.
     * @param address address to check
     * @return true if the given address is a valid EVM address length, false otherwise
     */
    private boolean isEvmAddress(@Nullable final Bytes address) {
        return address != null && isOfEvmAddressSize(address);
    }
}
