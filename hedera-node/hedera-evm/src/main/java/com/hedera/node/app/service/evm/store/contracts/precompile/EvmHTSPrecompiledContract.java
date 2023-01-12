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
package com.hedera.node.app.service.evm.store.contracts.precompile;

import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isTokenProxyRedirect;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isViewFunction;

import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import javax.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class EvmHTSPrecompiledContract {

    private final EvmInfrastructureFactory infrastructureFactory;
    //    private final ByteString networkId;

    @Inject
    public EvmHTSPrecompiledContract(EvmInfrastructureFactory infrastructureFactory) {
        this.infrastructureFactory = infrastructureFactory;
    }

    public Pair<Long, Bytes> computeCosted(
            final Bytes input,
            final MessageFrame frame,
            ViewGasCalculator viewGasCalculator,
            final TokenAccessor tokenAccessor) {
        if (isTokenProxyRedirect(input)) {
            final var executor =
                    infrastructureFactory.newRedirectExecutor(
                            input, frame, viewGasCalculator, tokenAccessor);
            return executor.computeCosted();
        } else if (isViewFunction(input)) {
            final var executor =
                    infrastructureFactory.newViewExecutor(
                            input, frame, viewGasCalculator, tokenAccessor);
            return executor.computeCosted();
        }

        return Pair.of(-1L, Bytes.EMPTY);
    }
}
