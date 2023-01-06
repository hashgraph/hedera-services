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
package com.hedera.node.app.service.evm.store.contracts.precompile.proxy;

import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.getRedirectTarget;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class RedirectViewExecutor {

    public static final long MINIMUM_TINYBARS_COST = 100;
    private final Bytes input;
    private final MessageFrame frame;
    private final EvmEncodingFacade evmEncoder;
    private final ViewGasCalculator gasCalculator;

    public RedirectViewExecutor(
            final Bytes input,
            final MessageFrame frame,
            final EvmEncodingFacade evmEncoder,
            final ViewGasCalculator gasCalculator) {
        this.input = input;
        this.frame = frame;
        this.evmEncoder = evmEncoder;
        this.gasCalculator = gasCalculator;
    }

    public static Timestamp asSecondsTimestamp(final long now) {
        return Timestamp.newBuilder().setSeconds(now).build();
    }

    public Pair<Long, Bytes> computeCosted() {
        final var target = getRedirectTarget(input);
        final var now = asSecondsTimestamp(frame.getBlockValues().getTimestamp());
        final var costInGas = gasCalculator.compute(now, MINIMUM_TINYBARS_COST);

        final var selector = target.descriptor();
        // TODO: fix
        //    final var isFungibleToken = FUNGIBLE_COMMON.equals(ledgers.typeOf(tokenId));
        final var isFungibleToken = true;

        try {
            final var answer = answerGiven(selector, target.token(), isFungibleToken);
            return Pair.of(costInGas, answer);
        } catch (final InvalidTransactionException e) {
            if (e.isReverting()) {
                frame.setRevertReason(e.getRevertReason());
                frame.setState(MessageFrame.State.REVERT);
            }
            return Pair.of(costInGas, null);
        }
    }

    private Bytes answerGiven(
            final int selector, final Address token, final boolean isFungibleToken) {

        return Bytes.EMPTY;
    }
}
