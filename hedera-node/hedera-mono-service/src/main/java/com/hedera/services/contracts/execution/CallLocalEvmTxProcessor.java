/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.execution;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

/**
 * Extension of the base {@link EvmTxProcessor} that provides interface for executing {@link
 * com.hederahashgraph.api.proto.java.ContractCallLocal} queries
 */
@Singleton
public class CallLocalEvmTxProcessor extends EvmTxProcessor {
    private final CodeCache codeCache;
    private final AliasManager aliasManager;

    @Inject
    public CallLocalEvmTxProcessor(
            final CodeCache codeCache,
            final LivePricesSource livePricesSource,
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<String, Provider<MessageCallProcessor>> mcps,
            final Map<String, Provider<ContractCreationProcessor>> ccps,
            final AliasManager aliasManager) {
        super(livePricesSource, dynamicProperties, gasCalculator, mcps, ccps);
        this.codeCache = codeCache;
        this.aliasManager = aliasManager;
    }

    @Override
    public void setWorldState(final HederaMutableWorldState worldState) {
        super.setWorldState(worldState);
    }

    @Override
    public void setBlockMetaSource(BlockMetaSource blockMetaSource) {
        super.setBlockMetaSource(blockMetaSource);
    }

    @Override
    protected HederaFunctionality getFunctionType() {
        return HederaFunctionality.ContractCallLocal;
    }

    public TransactionProcessingResult execute(
            final Account sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData) {
        final long gasPrice = 1;

        return super.execute(
                sender,
                receiver,
                gasPrice,
                providedGasLimit,
                value,
                callData,
                false,
                true,
                aliasManager.resolveForEvm(receiver),
                null,
                0,
                null);
    }

    @Override
    protected MessageFrame buildInitialFrame(
            final MessageFrame.Builder baseInitialFrame,
            final Address to,
            final Bytes payload,
            final long value) {
        final var code = codeCache.getIfPresent(aliasManager.resolveForEvm(to));
        /* It's possible we are racing the handleTransaction() thread, and the target contract's
         * _account_ has been created, but not yet its _bytecode_. So if `code` is null here,
         * it doesn't mean a system invariant has been violated (FAIL_INVALID); instead it means
         * the target contract is not yet in a valid state to be queried (INVALID_CONTRACT_ID). */
        validateTrue(code != null, INVALID_CONTRACT_ID);

        return baseInitialFrame
                .type(MessageFrame.Type.MESSAGE_CALL)
                .address(to)
                .contract(to)
                .inputData(payload)
                .code(code)
                .build();
    }
}
