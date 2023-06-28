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

package com.hedera.node.app.service.contract.impl.exec.utils;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CONFIG_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;

import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.config.data.LedgerConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayDeque;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.MessageFrame;

@Singleton
public class FrameBuilder {
    private static final int MAX_STACK_SIZE = 1024;

    @Inject
    public FrameBuilder() {
        // Dagger2
    }

    @SuppressWarnings("java:S107")
    public MessageFrame buildInitialFrameWith(
            @NonNull final HederaEvmTransaction transaction,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final HederaEvmContext context,
            @NonNull final Configuration config,
            @NonNull final Address from,
            @NonNull final Address to,
            final long intrinsicGas) {
        final var value = transaction.weiValue();
        final var ledgerConfig = config.getConfigData(LedgerConfig.class);
        final var nominalCoinbase = asLongZeroAddress(ledgerConfig.fundingAccount());
        final var builder = MessageFrame.builder()
                .messageFrameStack(new ArrayDeque<>())
                .maxStackSize(MAX_STACK_SIZE)
                .worldUpdater(worldUpdater.updater())
                .initialGas(transaction.gasAvailable(intrinsicGas))
                .originator(from)
                .gasPrice(Wei.of(context.gasPrice()))
                .sender(from)
                .value(value)
                .apparentValue(value)
                .blockValues(context.blockValuesOf(transaction.gasLimit()))
                .depth(0)
                .completer(unused -> {})
                .isStatic(context.staticCall())
                .miningBeneficiary(nominalCoinbase)
                .blockHashLookup(context.blocks()::blockHashOf)
                .contextVariables(Map.of(CONFIG_CONTEXT_VARIABLE, config));
        if (transaction.isCreate()) {
            return finishedAsCreate(to, builder, transaction);
        } else {
            return finishedAsCall(to, builder, context, transaction);
        }
    }

    private MessageFrame finishedAsCreate(
            @NonNull final Address to,
            @NonNull final MessageFrame.Builder builder,
            @NonNull final HederaEvmTransaction transaction) {
        return builder.type(MessageFrame.Type.CONTRACT_CREATION)
                .address(to)
                .contract(to)
                .inputData(Bytes.EMPTY)
                .code(CodeFactory.createCode(transaction.evmPayload(), 0, false))
                .build();
    }

    private MessageFrame finishedAsCall(
            @NonNull final Address to,
            @NonNull final MessageFrame.Builder builder,
            @NonNull final HederaEvmContext context,
            @NonNull final HederaEvmTransaction transaction) {
        final Code code;
        if (transaction.permitsMissingContract()) {
            code = context.loadIfPresent(to);
        } else {
            code = context.load(to);
        }
        return builder.type(MessageFrame.Type.MESSAGE_CALL)
                .address(to)
                .contract(to)
                .inputData(transaction.evmPayload())
                .code(code)
                .build();
    }
}
