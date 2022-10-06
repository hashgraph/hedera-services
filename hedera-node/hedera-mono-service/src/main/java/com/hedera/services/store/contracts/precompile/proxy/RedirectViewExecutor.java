/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.proxy;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.enums.TokenType.FUNGIBLE_COMMON;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_ALLOWANCE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_DECIMALS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_GET_APPROVED;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_OWNER_OF_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SYMBOL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN;
import static com.hedera.services.store.contracts.precompile.utils.DescriptorUtils.getRedirectTarget;
import static com.hedera.services.utils.MiscUtils.asSecondsTimestamp;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.AllowancePrecompile;
import com.hedera.services.store.contracts.precompile.impl.BalanceOfPrecompile;
import com.hedera.services.store.contracts.precompile.impl.GetApprovedPrecompile;
import com.hedera.services.store.contracts.precompile.impl.IsApprovedForAllPrecompile;
import com.hedera.services.store.contracts.precompile.impl.OwnerOfPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenURIPrecompile;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class RedirectViewExecutor {
    public static final long MINIMUM_TINYBARS_COST = 100;

    private final Bytes input;
    private final MessageFrame frame;
    private final WorldLedgers ledgers;
    private final EncodingFacade encoder;
    private final ViewGasCalculator gasCalculator;
    private final HederaStackedWorldStateUpdater updater;

    public RedirectViewExecutor(
            final Bytes input,
            final MessageFrame frame,
            final EncodingFacade encoder,
            final ViewGasCalculator gasCalculator) {
        this.input = input;
        this.frame = frame;
        this.encoder = encoder;
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
            return encoder.encodeName(name);
        } else if (selector == ABI_ID_ERC_SYMBOL) {
            final var symbol = ledgers.symbolOf(tokenId);
            return encoder.encodeSymbol(symbol);
        } else if (selector == ABI_ID_ERC_ALLOWANCE) {
            final var wrapper =
                    AllowancePrecompile.decodeTokenAllowance(
                            input.slice(24), tokenId, updater::unaliased);
            final var allowance =
                    ledgers.staticAllowanceOf(wrapper.owner(), wrapper.spender(), tokenId);
            return encoder.encodeAllowance(allowance);
        } else if (selector == ABI_ID_ERC_GET_APPROVED) {
            final var wrapper = GetApprovedPrecompile.decodeGetApproved(input.slice(24), tokenId);
            final var spender =
                    ledgers.staticApprovedSpenderOf(NftId.fromGrpc(tokenId, wrapper.serialNo()));
            final var priorityAddress = ledgers.canonicalAddress(spender);
            return encoder.encodeGetApproved(priorityAddress);
        } else if (selector == ABI_ID_ERC_IS_APPROVED_FOR_ALL) {
            final var wrapper =
                    IsApprovedForAllPrecompile.decodeIsApprovedForAll(
                            input.slice(24), tokenId, updater::unaliased);
            final var isOperator =
                    ledgers.staticIsOperator(wrapper.owner(), wrapper.operator(), tokenId);
            return encoder.encodeIsApprovedForAll(isOperator);
        } else if (selector == ABI_ID_ERC_DECIMALS) {
            validateTrue(isFungibleToken, INVALID_TOKEN_ID);
            final var decimals = ledgers.decimalsOf(tokenId);
            return encoder.encodeDecimals(decimals);
        } else if (selector == ABI_ID_ERC_TOTAL_SUPPLY_TOKEN) {
            final var totalSupply = ledgers.totalSupplyOf(tokenId);
            return encoder.encodeTotalSupply(totalSupply);
        } else if (selector == ABI_ID_ERC_BALANCE_OF_TOKEN) {
            final var wrapper =
                    BalanceOfPrecompile.decodeBalanceOf(input.slice(24), updater::unaliased);
            final var balance = ledgers.balanceOf(wrapper.accountId(), tokenId);
            return encoder.encodeBalance(balance);
        } else if (selector == ABI_ID_ERC_OWNER_OF_NFT) {
            validateFalse(isFungibleToken, INVALID_TOKEN_ID);
            final var wrapper = OwnerOfPrecompile.decodeOwnerOf(input.slice(24));
            final var nftId = NftId.fromGrpc(tokenId, wrapper.serialNo());
            final var owner = ledgers.ownerOf(nftId);
            final var priorityAddress = ledgers.canonicalAddress(owner);
            return encoder.encodeOwner(priorityAddress);
        } else if (selector == ABI_ID_ERC_TOKEN_URI_NFT) {
            validateFalse(isFungibleToken, INVALID_TOKEN_ID);
            final var wrapper = TokenURIPrecompile.decodeTokenUriNFT(input.slice(24));
            final var nftId = NftId.fromGrpc(tokenId, wrapper.serialNo());
            final var metadata = ledgers.metadataOf(nftId);
            return encoder.encodeTokenUri(metadata);
        } else {
            // Only view functions can be used inside a ContractCallLocal
            throw new InvalidTransactionException(NOT_SUPPORTED);
        }
    }
}
