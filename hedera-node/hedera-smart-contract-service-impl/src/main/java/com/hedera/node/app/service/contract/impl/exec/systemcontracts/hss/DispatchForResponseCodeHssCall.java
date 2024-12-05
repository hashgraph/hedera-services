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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.ERROR_DECODING_PRECOMPILE_INPUT;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.haltResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.encodedRc;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.contractsConfigOf;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.spi.workflows.DispatchOptions;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Set;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * An HSS call that simply dispatches a synthetic transaction body and returns a result that is
 * an encoded {@link ResponseCodeEnum}.
 */
public class DispatchForResponseCodeHssCall extends AbstractCall {

    private final AccountID senderId;

    @Nullable
    private final TransactionBody syntheticBody;

    private final VerificationStrategy verificationStrategy;
    private final DispatchGasCalculator dispatchGasCalculator;
    private final Set<Key> authorizingKeys;

    /**
     * Convenience overload that slightly eases construction for the most common case.
     *
     * @param attempt the attempt to translate to a dispatching
     * @param syntheticBody the synthetic body to dispatch
     * @param dispatchGasCalculator the dispatch gas calculator to use
     */
    public DispatchForResponseCodeHssCall(
            @NonNull final HssCallAttempt attempt,
            @Nullable final TransactionBody syntheticBody,
            @NonNull final DispatchGasCalculator dispatchGasCalculator,
            @NonNull final Set<Key> authorizingKeys) {
        this(
                attempt.enhancement(),
                attempt.systemContractGasCalculator(),
                attempt.addressIdConverter().convertSender(attempt.senderAddress()),
                syntheticBody,
                attempt.defaultVerificationStrategy(),
                dispatchGasCalculator,
                authorizingKeys);
    }

    /**
     * More general constructor, for cases where perhaps a custom {@link VerificationStrategy} is needed.
     *
     * @param enhancement the enhancement to use
     * @param senderId the id of the spender
     * @param syntheticBody the synthetic body to dispatch
     * @param verificationStrategy the verification strategy to use
     * @param dispatchGasCalculator the dispatch gas calculator to use
     */
    // too many parameters
    @SuppressWarnings("java:S107")
    public DispatchForResponseCodeHssCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final AccountID senderId,
            @Nullable final TransactionBody syntheticBody,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final DispatchGasCalculator dispatchGasCalculator,
            @NonNull final Set<Key> authorizingKeys) {
        super(gasCalculator, enhancement, false);
        this.senderId = Objects.requireNonNull(senderId);
        this.syntheticBody = syntheticBody;
        this.verificationStrategy = Objects.requireNonNull(verificationStrategy);
        this.dispatchGasCalculator = Objects.requireNonNull(dispatchGasCalculator);
        this.authorizingKeys = authorizingKeys;
    }

    @Override
    public @NonNull PricedResult execute(@NonNull final MessageFrame frame) {
        if (syntheticBody == null) {
            return gasOnly(
                    haltResult(
                            ERROR_DECODING_PRECOMPILE_INPUT,
                            contractsConfigOf(frame).precompileHtsDefaultGasCost()),
                    INVALID_TRANSACTION_BODY,
                    false);
        }
        final var recordBuilder = systemContractOperations()
                .dispatch(
                        syntheticBody,
                        verificationStrategy,
                        senderId,
                        ContractCallStreamBuilder.class,
                        authorizingKeys,
                        DispatchOptions.UsePresetTxnId.NO);
        final var gasRequirement =
                dispatchGasCalculator.gasRequirement(syntheticBody, gasCalculator, enhancement, senderId);
        var status = recordBuilder.status();
        if (status != SUCCESS) {
            recordBuilder.status(status);
        }
        return completionWith(gasRequirement, recordBuilder, encodedRc(recordBuilder.status()));
    }
}
