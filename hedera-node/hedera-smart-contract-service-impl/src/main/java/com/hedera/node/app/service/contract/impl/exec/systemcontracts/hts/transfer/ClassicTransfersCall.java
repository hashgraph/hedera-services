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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.haltResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.encodedRc;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersTranslator.TRANSFER_TOKEN;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.TransferEventLoggingUtils.logSuccessfulFungibleTransfer;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.TransferEventLoggingUtils.logSuccessfulNftTransfer;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Implements the "classic" HTS transfer calls, which differ from the ERC redirects in three notable ways:
 * <ol>
 *     <li>They accept the token address as an explicit parameter, instead of getting the token id
 *     via a redirect.</li>
 *     <li>They return the ordinal value of a non-successful {@link ResponseCodeEnum} instead of reverting
 *     like the ERC calls do.</li>
 *     <li>The legacy versions that don't support approvals will automatically "retry" their synthetic
 *     transaction using approvals for all non-sender debits if the initial attempt fails with
 *     {@link ResponseCodeEnum#INVALID_SIGNATURE} (which they translate to
 *     {@link ResponseCodeEnum#INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE}
 *     for backward compatibility).</li>
 * </ol>
 * But the basic pattern of constructing and dispatching a synthetic {@link CryptoTransferTransactionBody} remains.
 */
public class ClassicTransfersCall extends AbstractHtsCall {
    private final byte[] selector;
    private final AccountID senderId;
    private final ResponseCodeEnum preemptingFailureStatus;

    @Nullable
    private final TransactionBody syntheticTransfer;

    private final Configuration configuration;

    @Nullable
    private final ApprovalSwitchHelper approvalSwitchHelper;

    private final CallStatusStandardizer callStatusStandardizer;
    private final SystemAccountCreditScreen systemAccountCreditScreen;

    private final VerificationStrategy verificationStrategy;

    // too many parameters
    @SuppressWarnings("java:S107")
    public ClassicTransfersCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final byte[] selector,
            @NonNull final AccountID senderId,
            @Nullable final ResponseCodeEnum preemptingFailureStatus,
            @Nullable final TransactionBody syntheticTransfer,
            @NonNull final Configuration configuration,
            @Nullable ApprovalSwitchHelper approvalSwitchHelper,
            @NonNull final CallStatusStandardizer callStatusStandardizer,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final SystemAccountCreditScreen systemAccountCreditScreen) {
        super(gasCalculator, enhancement, false);
        this.selector = requireNonNull(selector);
        this.senderId = requireNonNull(senderId);
        this.preemptingFailureStatus = preemptingFailureStatus;
        this.syntheticTransfer = syntheticTransfer;
        this.configuration = requireNonNull(configuration);
        this.approvalSwitchHelper = approvalSwitchHelper;
        this.callStatusStandardizer = requireNonNull(callStatusStandardizer);
        this.systemAccountCreditScreen = systemAccountCreditScreen;
        this.verificationStrategy = requireNonNull(verificationStrategy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull PricedResult execute(@NonNull final MessageFrame frame) {
        if (syntheticTransfer == null) {
            return gasOnly(
                    haltResult(
                            configuration.getConfigData(ContractsConfig.class).precompileHtsDefaultGasCost()),
                    INVALID_TRANSACTION_BODY,
                    false);
        }
        final var gasRequirement =
                transferGasRequirement(syntheticTransfer, gasCalculator, enhancement, senderId, selector);
        if (preemptingFailureStatus != null) {
            return reversionWith(preemptingFailureStatus, gasRequirement);
        }
        if (systemAccountCreditScreen.creditsToSystemAccount(syntheticTransfer.cryptoTransferOrThrow())) {
            return reversionWith(
                    gasRequirement,
                    systemContractOperations()
                            .externalizePreemptedDispatch(syntheticTransfer, INVALID_RECEIVING_NODE_ACCOUNT));
        }
        if (executionIsNotSupported()) {
            return haltWith(
                    gasRequirement,
                    systemContractOperations().externalizePreemptedDispatch(syntheticTransfer, NOT_SUPPORTED));
        }
        final var transferToDispatch = shouldRetryWithApprovals()
                ? syntheticTransfer
                        .copyBuilder()
                        .cryptoTransfer(requireNonNull(approvalSwitchHelper)
                                .switchToApprovalsAsNeededIn(
                                        syntheticTransfer.cryptoTransferOrThrow(),
                                        systemContractOperations().activeSignatureTestWith(verificationStrategy),
                                        nativeOperations(),
                                        senderId))
                        .build()
                : syntheticTransfer;
        final var recordBuilder = systemContractOperations()
                .dispatch(transferToDispatch, verificationStrategy, senderId, ContractCallRecordBuilder.class);
        final var op = transferToDispatch.cryptoTransferOrThrow();
        if (recordBuilder.status() == SUCCESS) {
            maybeEmitErcLogsFor(op, frame);
        } else {
            recordBuilder.status(callStatusStandardizer.codeForFailure(recordBuilder.status(), frame, op));
        }
        return completionWith(gasRequirement, recordBuilder, encodedRc(recordBuilder.status()));
    }

    /**
     * Simulates the mono-service gas calculation for a classic transfer, which is significantly complicated by our
     * current strategy for setting the minimum tinybar price based on the canonical prices of various operations.
     *
     * @param body the transaction body to be dispatched
     * @param systemContractGasCalculator the gas calculator to use
     * @param enhancement the enhancement to use
     * @param payerId the payer of the transaction
     * @param selector the selector of the call
     * @return the gas requirement for the transaction to be dispatched
     */
    public static long transferGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId,
            @NonNull final byte[] selector) {
        final var op = body.cryptoTransferOrThrow();
        final var hasCustomFees = enhancement.nativeOperations().checkForCustomFees(op);
        // For fungible there are always at least two operations, so only charge half for each
        // operation
        final var baseUnitAdjustTinybarPrice = systemContractGasCalculator.canonicalPriceInTinybars(
                        hasCustomFees ? DispatchType.TRANSFER_FUNGIBLE_CUSTOM_FEES : DispatchType.TRANSFER_FUNGIBLE)
                / 2;
        // NFTs are atomic, one line can do it.
        final var baseNftTransferTinybarPrice = systemContractGasCalculator.canonicalPriceInTinybars(
                hasCustomFees ? DispatchType.TRANSFER_NFT_CUSTOM_FEES : DispatchType.TRANSFER_NFT);
        // Hbar transfer is similar to fungible tokens so only charge half for each operation
        final var baseHbarAdjustTinybarPrice =
                systemContractGasCalculator.canonicalPriceInTinybars(DispatchType.TRANSFER_HBAR) / 2;
        final var baseLazyCreationPrice =
                systemContractGasCalculator.canonicalPriceInTinybars(DispatchType.CRYPTO_CREATE)
                        + systemContractGasCalculator.canonicalPriceInTinybars(DispatchType.CRYPTO_UPDATE);

        final var extantAccounts = enhancement.nativeOperations().readableAccountStore();
        final long minimumTinybarPrice = minimumTinybarPriceGiven(
                op,
                baseUnitAdjustTinybarPrice,
                baseHbarAdjustTinybarPrice,
                baseNftTransferTinybarPrice,
                baseLazyCreationPrice,
                extantAccounts,
                selector);
        return systemContractGasCalculator.gasRequirement(body, payerId, minimumTinybarPrice);
    }

    private static long minimumTinybarPriceGiven(
            @NonNull final CryptoTransferTransactionBody op,
            final long baseUnitAdjustTinybarPrice,
            final long baseHbarAdjustTinybarPrice,
            final long baseNftTransferTinybarPrice,
            final long baseLazyCreationPrice,
            @NonNull final ReadableAccountStore extantAccounts,
            @NonNull final byte[] selector) {
        long minimumTinybarPrice = 0L;
        final var numHbarAdjusts = op.transfersOrElse(TransferList.DEFAULT)
                .accountAmountsOrElse(emptyList())
                .size();
        minimumTinybarPrice += numHbarAdjusts * baseHbarAdjustTinybarPrice;
        final Set<Bytes> aliasesToLazyCreate = new HashSet<>();
        for (final var tokenTransfers : op.tokenTransfersOrElse(emptyList())) {
            final var unitAdjusts = tokenTransfers.transfersOrElse(emptyList());
            // (FUTURE) Remove this divisor special case, done only for mono-service fidelity
            final var sizeDivisor = Arrays.equals(selector, TRANSFER_TOKEN.selector()) ? 2 : 1;
            minimumTinybarPrice += (unitAdjusts.size() / sizeDivisor) * baseUnitAdjustTinybarPrice;
            for (final var unitAdjust : unitAdjusts) {
                if (unitAdjust.amount() > 0
                        && unitAdjust.accountIDOrElse(AccountID.DEFAULT).hasAlias()) {
                    final var alias = unitAdjust.accountIDOrThrow().aliasOrThrow();
                    final var extantAccount = extantAccounts.getAccountIDByAlias(alias);
                    if (extantAccount == null) {
                        aliasesToLazyCreate.add(alias);
                    }
                }
            }
            final var nftTransfers = tokenTransfers.nftTransfersOrElse(emptyList());
            minimumTinybarPrice += nftTransfers.size() * baseNftTransferTinybarPrice;
            for (final var nftTransfer : nftTransfers) {
                if (nftTransfer.receiverAccountIDOrElse(AccountID.DEFAULT).hasAlias()) {
                    final var alias = nftTransfer.receiverAccountIDOrThrow().aliasOrThrow();
                    final var extantAccount = extantAccounts.getAccountIDByAlias(alias);
                    if (extantAccount == null) {
                        aliasesToLazyCreate.add(alias);
                    }
                }
            }
        }
        minimumTinybarPrice += aliasesToLazyCreate.size() * baseLazyCreationPrice;
        return minimumTinybarPrice;
    }

    private boolean shouldRetryWithApprovals() {
        return approvalSwitchHelper != null;
    }

    private boolean executionIsNotSupported() {
        return Arrays.equals(selector, ClassicTransfersTranslator.CRYPTO_TRANSFER_V2.selector())
                && !configuration.getConfigData(ContractsConfig.class).precompileAtomicCryptoTransferEnabled();
    }

    private void maybeEmitErcLogsFor(
            @NonNull final CryptoTransferTransactionBody op, @NonNull final MessageFrame frame) {
        if (Arrays.equals(ClassicTransfersTranslator.TRANSFER_FROM.selector(), selector)) {
            final var fungibleTransfers = op.tokenTransfersOrThrow().getFirst();
            logSuccessfulFungibleTransfer(
                    fungibleTransfers.tokenOrThrow(),
                    fungibleTransfers.transfersOrThrow(),
                    readableAccountStore(),
                    frame);
        } else if (Arrays.equals(ClassicTransfersTranslator.TRANSFER_NFT_FROM.selector(), selector)) {
            final var nftTransfers = op.tokenTransfersOrThrow().getFirst();
            logSuccessfulNftTransfer(
                    nftTransfers.tokenOrThrow(),
                    nftTransfers.nftTransfersOrThrow().getFirst(),
                    readableAccountStore(),
                    frame);
        }
    }
}
