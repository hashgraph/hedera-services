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

import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.EVM_ADDRESS_LEN;
import static com.hedera.node.app.service.mono.txns.crypto.AbstractAutoCreationLogic.AUTO_MEMO;
import static com.hedera.node.app.service.mono.txns.crypto.AbstractAutoCreationLogic.LAZY_MEMO;
import static com.hedera.node.app.service.mono.txns.crypto.AbstractAutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static com.hedera.node.app.service.token.impl.handlers.transfer.Utils.asKeyFromAlias;
import static com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator.IMMUTABILITY_SENTINEL_KEY;
import static com.hedera.node.app.spi.key.KeyUtils.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.swirlds.common.utility.CommonUtils.hex;

import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.AutoCreationConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import javax.inject.Inject;

public class AutoAccountCreationStep {
    private static final Logger log = LogManager.getLogger(AutoAccountCreationStep.class);
    private WritableAccountStore accountStore;
    private HandleContext handleContext;
    // checks tokenAliasMap if the change consists an alias that is already used in previous
    // iteration of the token transfer list. This map is used to count number of
    // maxAutoAssociations needed on auto created account
    protected final Map<Bytes, Set<TokenID>> tokenAliasMap = new HashMap<>();

    @Inject
    public AutoAccountCreationStep(
            @NonNull final WritableAccountStore accountStore,
            @NonNull final HandleContext handleContext) {
        this.handleContext = handleContext;
        this.accountStore = accountStore;
    }

    public long create(@NonNull final Bytes alias, final boolean isByTokenTransfer) {
        final var accountsConfig = handleContext.configuration().getConfigData(AccountsConfig.class);
        validateTrue(
                accountStore.sizeOfAccountState() + 1 <= accountsConfig.maxNumber(),
                ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);

        final var config = handleContext.configuration().getConfigData(AutoCreationConfig.class);
        validateTrue(config.enabled(), NOT_SUPPORTED);

        final var tokensConfig = handleContext.configuration().getConfigData(TokensConfig.class);
        if (isByTokenTransfer) {
            validateTrue(tokensConfig.autoCreationsIsEnabled(), ResponseCodeEnum.NOT_SUPPORTED);
        }

        final TransactionBody.Builder syntheticCreation;
        String memo;

        // checks tokenAliasMap if the change consists an alias that is already used in previous
        // iteration of the token transfer list. This map is used to count number of
        // maxAutoAssociations needed on auto created account
        if (isByTokenTransfer) {
            tokenAliasMap.putIfAbsent(alias, Collections.emptySet());
        }

        final var maxAutoAssociations =
                tokenAliasMap.getOrDefault(alias, Collections.emptySet()).size();
        final var isAliasEVMAddress = EntityIdUtils.isOfEvmAddressSize(alias);
        if (isAliasEVMAddress) {
            syntheticCreation = createHollowAccount(alias, 0L);
            memo = LAZY_MEMO;
        } else {
            final var key = asKeyFromAlias(alias);
            syntheticCreation = createAccount(alias, key, 0L, maxAutoAssociations);
            memo = AUTO_MEMO;
        }

        var fee = autoCreationFeeFor(syntheticCreation);
        if (isAliasEVMAddress) {
            fee += getLazyCreationFinalizationFee();
        }
        //TODO: Check if payer has enough balance to pay for the fee

        final var childRecord = handleContext.dispatchRemovableChildTransaction(
                syntheticCreation.memo(memo).build(), CryptoCreateRecordBuilder.class);

        if (!isAliasEVMAddress) {
            final var key = asKeyFromAlias(alias);
            if (key.hasEcdsaSecp256k1()) {
                final var evmAddress = tryAddressRecovery(key, EthSigsUtils::recoverAddressFromPubKey);
                childRecord.evmAddress(Bytes.wrap(evmAddress));
            }
        }
        // TODO: Not sure if fee should be set here
        //        childRecord.transactionFee(fee);
        return fee;
    }

    private long getLazyCreationFinalizationFee() {
        final var updateTxnBody = CryptoUpdateTransactionBody.newBuilder()
                .key(Key.newBuilder().ecdsaSecp256k1(Bytes.EMPTY).build());
        return autoCreationFeeFor(TransactionBody.newBuilder().cryptoUpdateAccount(updateTxnBody));
    }

    private long autoCreationFeeFor(final TransactionBody.Builder syntheticCreation) {
        final var topLevelPayer = handleContext.body().transactionIDOrThrow().accountIDOrThrow();
        final var payerAccount = accountStore.get(topLevelPayer);
        validateTrue(payerAccount != null, PAYER_ACCOUNT_NOT_FOUND);
        final var txn = Transaction.newBuilder().body(syntheticCreation.build()).build();
        final var fees =  handleContext.feeCalculator().computePayment(txn, payerAccount.key());
        return fees.serviceFee() + fees.networkFee() + fees.nodeFee();
    }

    public TransactionBody.Builder createHollowAccount(final Bytes alias, final long balance) {
        final var baseBuilder = createAccountBase(balance);
        baseBuilder.key(IMMUTABILITY_SENTINEL_KEY).alias(alias).memo(LAZY_MEMO);
        return TransactionBody.newBuilder().cryptoCreateAccount(baseBuilder.build());
    }

    private CryptoCreateTransactionBody.Builder createAccountBase(final long balance) {
        return CryptoCreateTransactionBody.newBuilder()
                .initialBalance(balance)
                .autoRenewPeriod(Duration.newBuilder().seconds(THREE_MONTHS_IN_SECONDS));
    }

    private TransactionBody.Builder createAccount(
            final Bytes alias, final Key key, final long balance, final int maxAutoAssociations) {
        final var baseBuilder = createAccountBase(balance);
        baseBuilder.key(key).alias(alias).memo(AUTO_MEMO);

        if (maxAutoAssociations > 0) {
            baseBuilder.maxAutomaticTokenAssociations(maxAutoAssociations);
        }
        return TransactionBody.newBuilder().cryptoCreateAccount(baseBuilder.build());
    }

    public static boolean isRecoveredEvmAddress(final Bytes address) {
        return address != null && address.length() == EVM_ADDRESS_LEN;
    }

    @Nullable
    public static byte[] tryAddressRecovery(@Nullable final Key key, final UnaryOperator<byte[]> addressRecovery) {
        if (key != null && key.hasEcdsaSecp256k1()) {
            // Only compressed keys are stored at the moment
            final var keyBytes = key.ecdsaSecp256k1();
            if (keyBytes.length() == ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH) {
                final var keyBytesArray = keyBytes.toByteArray();
                final var evmAddress = addressRecovery.apply(keyBytesArray);
                if (isRecoveredEvmAddress(Bytes.wrap(evmAddress))) {
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
}
