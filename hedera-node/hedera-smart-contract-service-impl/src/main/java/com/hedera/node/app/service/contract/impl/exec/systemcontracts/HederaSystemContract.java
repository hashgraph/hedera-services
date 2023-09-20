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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

/**
 * Interface for Hedera system contracts, which differ from standard EVM precompiles
 * in that they are not always able to report their gas requirement until they have
 * <i>nearly</i> computed their result.
 *
 * <p>For example, a {@code tokenTransfer()} system contract will much use more gas
 * if it involves custom fees. But computing the custom fees requires doing much of
 * the work of the precompile itself.
 */
public interface HederaSystemContract extends PrecompiledContract {
    record FullResult(@NonNull PrecompileContractResult result, long gasRequirement) {
        public FullResult {
            requireNonNull(result);
        }

        public Bytes output() {
            return result.getOutput();
        }

        public boolean isRefundGas() {
            return result.isRefundGas();
        }

        public static FullResult revertResult(@NonNull final ResponseCodeEnum reason, final long gasRequirement) {
            requireNonNull(reason);
            return new FullResult(
                    PrecompileContractResult.revert(
                            Bytes.wrap(reason.protoName().getBytes())),
                    gasRequirement);
        }

        public static FullResult haltResult(@NonNull final ExceptionalHaltReason reason, final long gasRequirement) {
            requireNonNull(reason);
            return new FullResult(PrecompileContractResult.halt(Bytes.EMPTY, Optional.of(reason)), gasRequirement);
        }

        public static FullResult successResult(@NonNull final ByteBuffer encoded, final long gasRequirement) {
            requireNonNull(encoded);
            return new FullResult(PrecompileContractResult.success(Bytes.wrap(encoded.array())), gasRequirement);
        }
    }

    /**
     * Computes the result of this contract, and also returns the gas requirement.
     *
     * @param input the input to the contract
     * @param messageFrame the message frame
     * @return the result of the computation, and its gas requirement
     */
    default FullResult computeFully(@NonNull Bytes input, @NonNull MessageFrame messageFrame) {
        return new FullResult(computePrecompile(input, messageFrame), gasRequirement(input));
    }
}
