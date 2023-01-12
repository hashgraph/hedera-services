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

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import javax.inject.Inject;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class EvmInfrastructureFactory {

    private final EvmEncodingFacade evmEncoder;

    @Inject
    public EvmInfrastructureFactory(EvmEncodingFacade evmEncoder) {
        this.evmEncoder = evmEncoder;
    }


    public RedirectViewExecutor newRedirectExecutor(
            final Bytes input, final MessageFrame frame, final ViewGasCalculator gasCalculator, final TokenAccessor tokenAccessor) {
        return new RedirectViewExecutor(input, frame, evmEncoder, gasCalculator, tokenAccessor);
    }

    public ViewExecutor newViewExecutor(
            final Bytes input,
            final MessageFrame frame,
            final ViewGasCalculator gasCalculator,
             final TokenAccessor tokenAccessor) {
        return new ViewExecutor(input, frame, evmEncoder, gasCalculator, tokenAccessor);
    }
}
