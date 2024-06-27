/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.evm.contracts.execution;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import java.util.Map;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.Builder;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

public class SpyHederaEvmTxProcessor extends HederaEvmTxProcessor {

    protected SpyHederaEvmTxProcessor(
            final HederaEvmMutableWorldState worldState,
            final PricesAndFeesProvider livePricesSource,
            final EvmProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<String, Provider<MessageCallProcessor>> mcps,
            final Map<String, Provider<ContractCreationProcessor>> ccps,
            final BlockMetaSource blockMetaSource) {
        super(worldState, livePricesSource, dynamicProperties, gasCalculator, mcps, ccps, blockMetaSource);
    }

    @Override
    protected HederaFunctionality getFunctionType() {
        return HederaFunctionality.CONTRACT_CALL;
    }

    @Override
    public MessageFrame buildInitialFrame(
            final Builder baseInitialFrame, final Address to, final Bytes payload, final long value) {
        return baseInitialFrame
                .type(MessageFrame.Type.MESSAGE_CALL)
                .address(to)
                .contract(to)
                .inputData(payload)
                .code(CodeV0.EMPTY_CODE)
                .build();
    }

    public AbstractMessageProcessor getMessageCallProcessor() {
        return messageCallProcessor;
    }
}
