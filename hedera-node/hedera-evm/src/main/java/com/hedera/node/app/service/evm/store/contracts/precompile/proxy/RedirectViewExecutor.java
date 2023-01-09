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

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_ALLOWANCE;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_DECIMALS;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_GET_APPROVED;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_OWNER_OF_NFT;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SYMBOL;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.getRedirectTarget;
import static com.hedera.node.app.service.evm.store.tokens.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmAllowancePrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmBalanceOfPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetApprovedPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmIsApprovedForAllPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmOwnerOfPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmTokenURIPrecompile;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
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
    private final TokenAccessor tokenAccessor;

    public RedirectViewExecutor(
            final Bytes input,
            final MessageFrame frame,
            final EvmEncodingFacade evmEncoder,
            final ViewGasCalculator gasCalculator,
            final TokenAccessor tokenAccessor) {
        this.input = input;
        this.frame = frame;
        this.evmEncoder = evmEncoder;
        this.gasCalculator = gasCalculator;
        this.tokenAccessor = tokenAccessor;
    }

    public static Timestamp asSecondsTimestamp(final long now) {
        return Timestamp.newBuilder().setSeconds(now).build();
    }

    public Pair<Long, Bytes> computeCosted() {
        final var target = getRedirectTarget(input);
        final var now = asSecondsTimestamp(frame.getBlockValues().getTimestamp());
        final var costInGas = gasCalculator.compute(now, MINIMUM_TINYBARS_COST);
        final var selector = target.descriptor();
        final var isFungibleToken = FUNGIBLE_COMMON.equals(tokenAccessor.typeOf(target.token()));
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
        if (selector == ABI_ID_ERC_NAME) {
            final var name = tokenAccessor.nameOf(token);
            return evmEncoder.encodeName(name);
        } else if (selector == ABI_ID_ERC_SYMBOL) {
            final var symbol = tokenAccessor.symbolOf(token);
            return evmEncoder.encodeSymbol(symbol);
        } else if (selector == ABI_ID_ERC_ALLOWANCE) {
            final var wrapper =
                EvmAllowancePrecompile.decodeTokenAllowance(input);
            final var allowance =
                tokenAccessor.staticAllowanceOf(Address.wrap(Bytes.wrap(wrapper.owner())), Address.wrap(Bytes.wrap(wrapper.spender())), Address.wrap(Bytes.wrap(wrapper.token())));
            return evmEncoder.encodeAllowance(allowance);
        } else if (selector == ABI_ID_ERC_GET_APPROVED) {
            final var wrapper = EvmGetApprovedPrecompile.decodeGetApproved(input);
            final var spender =
                tokenAccessor.staticApprovedSpenderOf(Address.wrap(Bytes.wrap(wrapper.token())), wrapper.serialNo());
            final var priorityAddress = tokenAccessor.canonicalAddress(spender);
            return evmEncoder.encodeGetApproved(priorityAddress);
        } else if (selector == ABI_ID_ERC_IS_APPROVED_FOR_ALL) {
            final var wrapper =
                EvmIsApprovedForAllPrecompile.decodeIsApprovedForAll(input);
            final var isOperator =
                tokenAccessor.staticIsOperator(Address.wrap(Bytes.wrap(wrapper.owner())), Address.wrap(Bytes.wrap(wrapper.operator())), Address.wrap(Bytes.wrap(wrapper.token())));
            return evmEncoder.encodeIsApprovedForAll(isOperator);
        }
        else if (selector == ABI_ID_ERC_DECIMALS) {
//            validateTrue(isFungibleToken, INVALID_TOKEN_ID);
            final var decimals = tokenAccessor.decimalsOf(token);
            return evmEncoder.encodeDecimals(decimals);
        } else if (selector == ABI_ID_ERC_TOTAL_SUPPLY_TOKEN) {
            final var totalSupply = tokenAccessor.totalSupplyOf(token);
            return evmEncoder.encodeTotalSupply(totalSupply);
        } else if (selector == ABI_ID_ERC_BALANCE_OF_TOKEN) {
            final var wrapper =
                EvmBalanceOfPrecompile.decodeBalanceOf(input.slice(24));
            final var balance = tokenAccessor.balanceOf(Address.wrap(Bytes.wrap(wrapper.account())), token);
            return evmEncoder.encodeBalance(balance);
        } else if (selector == ABI_ID_ERC_OWNER_OF_NFT) {
//            validateFalse(isFungibleToken, INVALID_TOKEN_ID);
            final var wrapper = EvmOwnerOfPrecompile.decodeOwnerOf(input.slice(24));
            final var owner = tokenAccessor.ownerOf(token, wrapper.serialNo());
            final var priorityAddress = tokenAccessor.canonicalAddress(owner);
            return evmEncoder.encodeOwner(priorityAddress);
        } else if (selector == ABI_ID_ERC_TOKEN_URI_NFT) {
//            validateFalse(isFungibleToken, INVALID_TOKEN_ID);
            final var wrapper = EvmTokenURIPrecompile.decodeTokenUriNFT(input.slice(24));
            final var metadata = tokenAccessor.metadataOf(token, wrapper.serialNo());
            return evmEncoder.encodeTokenUri(metadata);
        } else {
            throw new InvalidTransactionException(NOT_SUPPORTED);
        }
    }
}
