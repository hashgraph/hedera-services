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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations;

import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isTokenProxyRedirect;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isViewFunction;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.token.records.TokenUpdateRecordBuilder;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class AssociationsCall extends AbstractHtsCall {

    private final AccountID senderId;
    private final TransactionBody syntheticBody;
    private final VerificationStrategy verificationStrategy;
    private final DispatchGasCalculator dispatchGasCalculator;

    public AssociationsCall(
            @NonNull final HtsCallAttempt attempt,
            @NonNull final TransactionBody syntheticBody,
            @NonNull final DispatchGasCalculator dispatchGasCalculator) {
        this(
                attempt.enhancement(),
                attempt.systemContractGasCalculator(),
                attempt.addressIdConverter().convertSender(attempt.senderAddress()),
                syntheticBody,
                attempt.defaultVerificationStrategy(),
                dispatchGasCalculator);
    }

    public AssociationsCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final AccountID senderId,
            @NonNull final TransactionBody syntheticBody,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final DispatchGasCalculator dispatchGasCalculator) {
        super(gasCalculator, enhancement);
        this.senderId = Objects.requireNonNull(senderId);
        this.syntheticBody = Objects.requireNonNull(syntheticBody);
        this.verificationStrategy = Objects.requireNonNull(verificationStrategy);
        this.dispatchGasCalculator = Objects.requireNonNull(dispatchGasCalculator);
    }

    @NonNull
    @Override
    public PricedResult execute() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @NonNull
    @Override
    public PricedResult execute(MessageFrame frame) {
        TokenUpdateRecordBuilder recordBuilder;

        if (frame.isStatic() && !isTokenProxyRedirect(frame.getInputData()) && !isViewFunction(frame.getInputData())) {
            recordBuilder = systemContractOperations()
                    .dispatchRemovable(
                            syntheticBody,
                            verificationStrategy,
                            senderId,
                            TokenUpdateRecordBuilder.class,
                            ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER);
        } else {
            recordBuilder = systemContractOperations()
                    .dispatch(syntheticBody, verificationStrategy, senderId, TokenUpdateRecordBuilder.class);
        }

        final var gasRequirement =
                dispatchGasCalculator.gasRequirement(syntheticBody, gasCalculator, enhancement, senderId);

        var output = ReturnTypes.encodedRc(standardized(recordBuilder.status()));
        recordBuilder.contractCallResult(ContractFunctionResult.newBuilder()
                .contractCallResult(Bytes.wrap(output.array()))
                .build());

        // should here be status with failureCustomizer ?

        return completionWith(recordBuilder.status(), gasRequirement);
    }
}
