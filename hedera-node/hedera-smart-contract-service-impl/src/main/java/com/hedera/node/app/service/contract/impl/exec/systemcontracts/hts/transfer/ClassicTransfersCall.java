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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;

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
    private final AccountID spenderId;
    private final TransactionBody syntheticTransfer;
    private final Configuration configuration;

    @Nullable
    private final ApprovalSwitchHelper approvalSwitchHelper;

    private final SystemAccountCreditScreen systemAccountCreditScreen;

    private final VerificationStrategy verificationStrategy;

    // too many parameters
    @SuppressWarnings("java:S107")
    public ClassicTransfersCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final byte[] selector,
            @NonNull final AccountID spenderId,
            @NonNull final TransactionBody syntheticTransfer,
            @NonNull final Configuration configuration,
            @Nullable ApprovalSwitchHelper approvalSwitchHelper,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final SystemAccountCreditScreen systemAccountCreditScreen) {
        super(gasCalculator, enhancement);
        this.selector = requireNonNull(selector);
        this.spenderId = requireNonNull(spenderId);
        this.syntheticTransfer = requireNonNull(syntheticTransfer);
        this.configuration = requireNonNull(configuration);
        this.approvalSwitchHelper = approvalSwitchHelper;
        this.systemAccountCreditScreen = systemAccountCreditScreen;
        this.verificationStrategy = requireNonNull(verificationStrategy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull PricedResult execute() {
        // https://github.com/hashgraph/hedera-smart-contracts/blob/main/contracts/hts-precompile/IHederaTokenService.sol
        // TODO - gas calculation
        if (systemAccountCreditScreen.creditsToSystemAccount(syntheticTransfer.cryptoTransferOrThrow())) {
            // TODO - externalize the invalid synthetic transfer without dispatching it
            return reversionWith(INVALID_RECEIVING_NODE_ACCOUNT, 0L);
        }
        if (executionIsNotSupported()) {
            // TODO - externalize the unsupported synthetic transfer without dispatching it
            return completionWith(NOT_SUPPORTED, 0L);
        }
        final var transferToDispatch = shouldRetryWithApprovals()
                ? syntheticTransfer
                        .copyBuilder()
                        .cryptoTransfer(requireNonNull(approvalSwitchHelper)
                                .switchToApprovalsAsNeededIn(
                                        syntheticTransfer.cryptoTransferOrThrow(),
                                        systemContractOperations().activeSignatureTestWith(verificationStrategy),
                                        nativeOperations()))
                        .build()
                : syntheticTransfer;
        final var recordBuilder = systemContractOperations()
                .dispatch(transferToDispatch, verificationStrategy, spenderId, CryptoTransferRecordBuilder.class);
        return completionWith(recordBuilder.status(), 0L);
    }

    public static long classicTransferGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        throw new AssertionError("Not implemented");

        //        final var op = body.cryptoTransfer();
        //        final var hasCustomFees = enhancement.nativeOperations().checkForCustomFees(op);
        //        // Follow mono-service here
        //        final var baseHbarAdjustTinybarPrice =
        // systemContractGasCalculator.canonicalGasRequirement(DispatchType.TRANSFER_HBAR) / 2;
        //        final var baseUnitAdjustTinybarPrice = systemContractGasCalculator.canonicalGasRequirement(
        //                hasCustomFees ? DispatchType.TRANSFER_FUNGIBLE_CUSTOM_FEES : DispatchType.TRANSFER_FUNGIBLE) /
        // 2;
        //        final var baseNftTransferTinybarPrice = systemContractGasCalculator.canonicalGasRequirement(
        //                hasCustomFees ? DispatchType.TRANSFER_NFT_CUSTOM_FEES : DispatchType.TRANSFER_NFT);
        //        final var extantAccounts = enhancement.nativeOperations().readableAccountStore();

    }

    private boolean shouldRetryWithApprovals() {
        return approvalSwitchHelper != null;
    }

    private boolean executionIsNotSupported() {
        return Arrays.equals(selector, ClassicTransfersTranslator.CRYPTO_TRANSFER_V2.selector())
                && !configuration.getConfigData(ContractsConfig.class).precompileAtomicCryptoTransferEnabled();
    }
}
