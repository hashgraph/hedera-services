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

package com.hedera.node.app.service.contract.impl.infra;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_FILE_EMPTY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ERROR_DECODING_BYTESTRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SERIALIZATION_FAILED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.WRONG_CHAIN_ID;
import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction.NOT_APPLICABLE;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asChainIdBytes;
import static com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils.synthEthTxCreation;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;
import static org.apache.tuweni.bytes.Bytes.EMPTY;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.service.contract.impl.annotations.InitialState;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.hevm.HydratedEthTxData;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

@TransactionScope
public class HevmTransactionFactory {
    private static final long INTRINSIC_GAS_LOWER_BOUND = 21_000L;

    private final NetworkInfo networkInfo;
    private final LedgerConfig ledgerConfig;
    private final HederaConfig hederaConfig;
    private final GasCalculator gasCalculator;
    private final StakingConfig stakingConfig;
    private final ContractsConfig contractsConfig;
    private final ReadableFileStore fileStore;
    private final TokenServiceApi tokenServiceApi;
    private final ReadableAccountStore accountStore;
    private final ExpiryValidator expiryValidator;
    private final AttributeValidator attributeValidator;
    private final HydratedEthTxData hydratedEthTxData;
    private final EthTxSigsCache ethereumSignatures;

    @Inject
    public HevmTransactionFactory(
            @NonNull final NetworkInfo networkInfo,
            @NonNull final LedgerConfig ledgerConfig,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final GasCalculator gasCalculator,
            @NonNull final StakingConfig stakingConfig,
            @NonNull final ContractsConfig contractsConfig,
            @Nullable final HydratedEthTxData hydratedEthTxData,
            @NonNull @InitialState final ReadableAccountStore accountStore,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull @InitialState final ReadableFileStore fileStore,
            @NonNull final AttributeValidator attributeValidator,
            @NonNull @InitialState final TokenServiceApi tokenServiceApi,
            @NonNull final EthTxSigsCache ethereumSignatures) {
        this.hydratedEthTxData = hydratedEthTxData;
        this.gasCalculator = requireNonNull(gasCalculator);
        this.fileStore = requireNonNull(fileStore);
        this.networkInfo = requireNonNull(networkInfo);
        this.accountStore = requireNonNull(accountStore);
        this.ledgerConfig = requireNonNull(ledgerConfig);
        this.hederaConfig = requireNonNull(hederaConfig);
        this.stakingConfig = requireNonNull(stakingConfig);
        this.contractsConfig = requireNonNull(contractsConfig);
        this.tokenServiceApi = requireNonNull(tokenServiceApi);
        this.expiryValidator = requireNonNull(expiryValidator);
        this.attributeValidator = requireNonNull(attributeValidator);
        this.ethereumSignatures = ethereumSignatures;
    }

    /**
     * Given a {@link TransactionBody}, creates the implied {@link HederaEvmTransaction}.
     *
     * @param body the {@link TransactionBody} to convert
     * @return the implied {@link HederaEvmTransaction}
     * @throws IllegalArgumentException if the {@link TransactionBody} is not a contract operation
     */
    public HederaEvmTransaction fromHapiTransaction(@NonNull final TransactionBody body) {
        return switch (body.data().kind()) {
            case CONTRACT_CREATE_INSTANCE -> fromHapiCreate(
                    body.transactionIDOrThrow().accountIDOrThrow(), body.contractCreateInstanceOrThrow());
            case CONTRACT_CALL -> fromHapiCall(
                    body.transactionIDOrThrow().accountIDOrThrow(), body.contractCallOrThrow());
            case ETHEREUM_TRANSACTION -> fromHapiEthereum(
                    body.transactionIDOrThrow().accountIDOrThrow(), body.ethereumTransactionOrThrow());
            default -> throw new IllegalArgumentException("Not a contract operation");
        };
    }

    private HederaEvmTransaction fromHapiCreate(
            @NonNull final AccountID payer, @NonNull final ContractCreateTransactionBody body) {
        assertValidCreation(body);
        final var payload = initcodeFor(body);
        return new HederaEvmTransaction(
                payer,
                null,
                null,
                NOT_APPLICABLE,
                payload,
                null,
                body.initialBalance(),
                body.gas(),
                NOT_APPLICABLE,
                NOT_APPLICABLE,
                body);
    }

    private HederaEvmTransaction fromHapiCall(
            @NonNull final AccountID payer, @NonNull final ContractCallTransactionBody body) {
        assertValidCall(body);
        return new HederaEvmTransaction(
                payer,
                null,
                body.contractIDOrThrow(),
                NOT_APPLICABLE,
                body.functionParameters(),
                null,
                body.amount(),
                body.gas(),
                NOT_APPLICABLE,
                NOT_APPLICABLE,
                null);
    }

    private HederaEvmTransaction fromHapiEthereum(
            @NonNull final AccountID payerId, @NonNull final EthereumTransactionBody body) {
        final var ethTxData = assertValidEthTx(body);
        final var ethTxSig = ethereumSignatures.computeIfAbsent(ethTxData);
        final var senderId =
                AccountID.newBuilder().alias(Bytes.wrap(ethTxSig.address())).build();
        return ethTxData.hasToAddress()
                ? fromEthTxCall(payerId, senderId, ethTxData, body.maxGasAllowance())
                : fromEthTxCreate(payerId, senderId, ethTxData, body.maxGasAllowance());
    }

    private @NonNull HederaEvmTransaction fromEthTxCall(
            @NonNull final AccountID relayerId,
            @NonNull final AccountID senderId,
            @NonNull final EthTxData ethTxData,
            final long maxGasAllowance) {
        return new HederaEvmTransaction(
                senderId,
                relayerId,
                ContractID.newBuilder().evmAddress(Bytes.wrap(ethTxData.to())).build(),
                ethTxData.nonce(),
                ethTxData.hasCallData() ? Bytes.wrap(ethTxData.callData()) : Bytes.EMPTY,
                Bytes.wrap(ethTxData.chainId()),
                ethTxData.effectiveTinybarValue(),
                ethTxData.gasLimit(),
                ethTxData.effectiveOfferedGasPriceInTinybars(),
                maxGasAllowance,
                null);
    }

    private @NonNull HederaEvmTransaction fromEthTxCreate(
            @NonNull final AccountID relayerId,
            @NonNull final AccountID senderId,
            @NonNull final EthTxData ethTxData,
            final long maxGasAllowance) {
        return new HederaEvmTransaction(
                senderId,
                relayerId,
                null,
                ethTxData.nonce(),
                Bytes.wrap(ethTxData.callData()),
                Bytes.wrap(ethTxData.chainId()),
                ethTxData.effectiveTinybarValue(),
                ethTxData.gasLimit(),
                ethTxData.effectiveOfferedGasPriceInTinybars(),
                maxGasAllowance,
                synthEthTxCreation(ledgerConfig.autoRenewPeriodMinDuration(), ethTxData));
    }

    private @NonNull EthTxData assertValidEthTx(@NonNull final EthereumTransactionBody body) {
        validateTrue(body.maxGasAllowance() >= 0, NEGATIVE_ALLOWANCE_AMOUNT);
        if (!requireNonNull(hydratedEthTxData).isAvailable()) {
            throw new HandleException(hydratedEthTxData.status());
        }
        final var ethTxData = requireNonNull(hydratedEthTxData.ethTxData());
        validateTrue(ethTxData.matchesChainId(asChainIdBytes(contractsConfig.chainId())), WRONG_CHAIN_ID);
        validateTrue(ethTxData.hasToAddress() || ethTxData.hasCallData(), INVALID_ETHEREUM_TRANSACTION);
        return ethTxData;
    }

    private void assertValidCall(@NonNull final ContractCallTransactionBody body) {
        final var minGasLimit =
                Math.max(INTRINSIC_GAS_LOWER_BOUND, gasCalculator.transactionIntrinsicGasCost(EMPTY, false));
        validateTrue(body.gas() >= minGasLimit, INSUFFICIENT_GAS);
        validateTrue(body.amount() >= 0, CONTRACT_NEGATIVE_VALUE);
        validateTrue(body.gas() <= contractsConfig.maxGasPerSec(), MAX_GAS_LIMIT_EXCEEDED);

        final var contract = accountStore.getContractById(body.contractIDOrThrow());
        if (contract != null) {
            validateFalse(contract.deleted(), CONTRACT_DELETED);
        }
    }

    private void assertValidCreation(@NonNull final ContractCreateTransactionBody body) {
        if (body.hasFileID()) {
            validateTrue(body.fileIDOrThrow().fileNum() >= hederaConfig.firstUserEntity(), INVALID_FILE_ID);
        }
        final var autoRenewPeriod = body.autoRenewPeriodOrElse(Duration.DEFAULT).seconds();
        validateTrue(autoRenewPeriod >= 1, INVALID_RENEWAL_PERIOD);
        attributeValidator.validateAutoRenewPeriod(autoRenewPeriod);
        validateTrue(body.gas() >= 0, CONTRACT_NEGATIVE_GAS);
        validateTrue(body.initialBalance() >= 0, CONTRACT_NEGATIVE_VALUE);
        validateTrue(body.gas() <= contractsConfig.maxGasPerSec(), MAX_GAS_LIMIT_EXCEEDED);
        final var usesUnsupportedAutoAssociations =
                body.maxAutomaticTokenAssociations() > 0 && !contractsConfig.allowAutoAssociations();
        validateFalse(usesUnsupportedAutoAssociations, NOT_SUPPORTED);
        validateTrue(
                body.maxAutomaticTokenAssociations() <= ledgerConfig.maxAutoAssociations(),
                REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);
        final var usesNonDefaultProxyId = body.hasProxyAccountID() && !AccountID.DEFAULT.equals(body.proxyAccountID());
        validateFalse(usesNonDefaultProxyId, PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED);
        tokenServiceApi.assertValidStakingElection(
                stakingConfig.isEnabled(),
                body.declineReward(),
                body.stakedId().kind().name(),
                body.stakedAccountId(),
                body.stakedNodeId(),
                accountStore,
                networkInfo);
        attributeValidator.validateMemo(body.memo());
        final var effectiveKey = body.adminKeyOrElse(Key.DEFAULT);
        if (!isEmpty(effectiveKey)) {
            try {
                attributeValidator.validateKey(body.adminKeyOrElse(Key.DEFAULT));
            } catch (Exception ignore) {
                throw new HandleException(SERIALIZATION_FAILED);
            }
        }
        expiryValidator.resolveCreationAttempt(
                true,
                new ExpiryMeta(
                        NA, autoRenewPeriod, body.hasAutoRenewAccountId() ? body.autoRenewAccountIdOrThrow() : null),
                false);
    }

    private Bytes initcodeFor(@NonNull final ContractCreateTransactionBody body) {
        if (body.hasInitcode()) {
            return body.initcode();
        } else {
            final var initcode = fileStore.getFileLeaf(body.fileIDOrElse(FileID.DEFAULT));
            validateFalse(initcode == null, INVALID_FILE_ID);
            validateFalse(initcode.deleted(), FILE_DELETED);
            validateTrue(initcode.contents().length() > 0, CONTRACT_FILE_EMPTY);
            try {
                return Bytes.fromHex(new String(initcode.contents().toByteArray())
                        + body.constructorParameters().toHex());
            } catch (Exception ignore) {
                throw new HandleException(ERROR_DECODING_BYTESTRING);
            }
        }
    }
}
