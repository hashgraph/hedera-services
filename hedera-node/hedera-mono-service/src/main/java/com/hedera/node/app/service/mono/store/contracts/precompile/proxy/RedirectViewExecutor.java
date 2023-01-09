/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts.precompile.proxy;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.mono.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.AllowancePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.BalanceOfPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetApprovedPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.IsApprovedForAllPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.OwnerOfPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenURIPrecompile;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

import static com.hedera.node.app.service.evm.store.tokens.TokenType.FUNGIBLE_COMMON;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_ALLOWANCE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_DECIMALS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_GET_APPROVED;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_OWNER_OF_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SYMBOL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.utils.DescriptorUtils.getRedirectTarget;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asSecondsTimestamp;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

public class RedirectViewExecutor {
    public static final long MINIMUM_TINYBARS_COST = 100;

    private final Bytes input;
    private final MessageFrame frame;
    private final WorldLedgers ledgers;
    private final EvmEncodingFacade evmEncoder;
    private final ViewGasCalculator gasCalculator;
    private final HederaStackedWorldStateUpdater updater;

    public RedirectViewExecutor(
            final Bytes input,
            final MessageFrame frame,
            final EvmEncodingFacade evmEncoder,
            final ViewGasCalculator gasCalculator) {
        this.input = input;
        this.frame = frame;
        this.evmEncoder = evmEncoder;
        this.gasCalculator = gasCalculator;

        this.updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
        this.ledgers = updater.trackingLedgers();
    }

    public Pair<Long, Bytes> computeCosted() {
        final var target = getRedirectTarget(input);
        final var tokenId = target.tokenId();
        final var now = asSecondsTimestamp(frame.getBlockValues().getTimestamp());
        final var costInGas = gasCalculator.compute(now, MINIMUM_TINYBARS_COST);

        final var selector = target.descriptor();
        final var isFungibleToken = FUNGIBLE_COMMON.equals(ledgers.typeOf(tokenId));
        try {
            final var answer = answerGiven(selector, tokenId, isFungibleToken);
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
            final int selector, final TokenID tokenId, final boolean isFungibleToken) {
        if (selector == ABI_ID_ERC_NAME) {
            final var name = ledgers.nameOf(tokenId);
            return evmEncoder.encodeName(name);
        } else if (selector == ABI_ID_ERC_SYMBOL) {
            final var symbol = ledgers.symbolOf(tokenId);
            return evmEncoder.encodeSymbol(symbol);
        } else if (selector == ABI_ID_ERC_ALLOWANCE) {
            final var wrapper =
                    AllowancePrecompile.decodeTokenAllowance(input, tokenId, updater::unaliased);
            final var allowance =
                    ledgers.staticAllowanceOf(wrapper.owner(), wrapper.spender(), tokenId);
            return evmEncoder.encodeAllowance(allowance);
        } else if (selector == ABI_ID_ERC_GET_APPROVED) {
            final var wrapper = GetApprovedPrecompile.decodeGetApproved(input, tokenId);
            final var spender =
                    ledgers.staticApprovedSpenderOf(NftId.fromGrpc(tokenId, wrapper.serialNo()));
            final var priorityAddress = ledgers.canonicalAddress(spender);
            return evmEncoder.encodeGetApproved(priorityAddress);
        } else if (selector == ABI_ID_ERC_IS_APPROVED_FOR_ALL) {
            final var wrapper =
                    IsApprovedForAllPrecompile.decodeIsApprovedForAll(
                            input, tokenId, updater::unaliased);
            final var isOperator =
                    ledgers.staticIsOperator(wrapper.owner(), wrapper.operator(), tokenId);
            return evmEncoder.encodeIsApprovedForAll(isOperator);
        } else if (selector == ABI_ID_ERC_DECIMALS) {
            validateTrue(isFungibleToken, INVALID_TOKEN_ID);
            final var decimals = ledgers.decimalsOf(tokenId);
            return evmEncoder.encodeDecimals(decimals);
        } else if (selector == ABI_ID_ERC_TOTAL_SUPPLY_TOKEN) {
            final var totalSupply = ledgers.totalSupplyOf(tokenId);
            return evmEncoder.encodeTotalSupply(totalSupply);
        } else if (selector == ABI_ID_ERC_BALANCE_OF_TOKEN) {
            final var wrapper =
                    BalanceOfPrecompile.decodeBalanceOf(input.slice(24), updater::unaliased);
            final var balance = ledgers.balanceOf(wrapper.account(), tokenId);
            return evmEncoder.encodeBalance(balance);
        } else if (selector == ABI_ID_ERC_OWNER_OF_NFT) {
            validateFalse(isFungibleToken, INVALID_TOKEN_ID);
            final var wrapper = OwnerOfPrecompile.decodeOwnerOf(input.slice(24));
            final var nftId = NftId.fromGrpc(tokenId, wrapper.serialNo());
            final var owner = ledgers.ownerOf(nftId);
            final var priorityAddress = ledgers.canonicalAddress(owner);
            return evmEncoder.encodeOwner(priorityAddress);
        } else if (selector == ABI_ID_ERC_TOKEN_URI_NFT) {
            validateFalse(isFungibleToken, INVALID_TOKEN_ID);
            final var wrapper = TokenURIPrecompile.decodeTokenUriNFT(input.slice(24));
            final var nftId = NftId.fromGrpc(tokenId, wrapper.serialNo());
            final var metadata = ledgers.metadataOf(nftId);
            return evmEncoder.encodeTokenUri(metadata);
        } else {
            // Only view functions can be used inside a ContractCallLocal
            throw new InvalidTransactionException(NOT_SUPPORTED);
        }
    }
}
