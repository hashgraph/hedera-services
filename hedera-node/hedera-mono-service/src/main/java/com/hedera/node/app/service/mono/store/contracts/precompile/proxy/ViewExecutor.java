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

import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_KEY;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_TYPE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_IS_FROZEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_IS_KYC;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_IS_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompileUtils.buildKeyValueWrapper;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asSecondsTimestamp;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.FungibleTokenInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenDefaultFreezeStatus;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenDefaultKycStatus;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenExpiryInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenKeyPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenTypePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.IsFrozenPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.IsKycPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.IsTokenPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.NonFungibleTokenInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenGetCustomFeesPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenInfoPrecompile;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Objects;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class ViewExecutor {
    public static final long MINIMUM_TINYBARS_COST = 100;

    private final Bytes input;
    private final MessageFrame frame;
    private final EncodingFacade encoder;
    private final EvmEncodingFacade evmEncoder;
    private final ViewGasCalculator gasCalculator;
    private final StateView stateView;
    private final WorldLedgers ledgers;
    private final HederaStackedWorldStateUpdater updater;

    public ViewExecutor(
            final Bytes input,
            final MessageFrame frame,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final ViewGasCalculator gasCalculator,
            final StateView stateView) {
        this.input = input;
        this.frame = frame;
        this.encoder = encoder;
        this.evmEncoder = evmEncoder;
        this.gasCalculator = gasCalculator;
        this.stateView = stateView;
        this.updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
        this.ledgers = updater.trackingLedgers();
    }

    public Pair<Long, Bytes> computeCosted() {
        final var now = asSecondsTimestamp(frame.getBlockValues().getTimestamp());
        final var costInGas = gasCalculator.compute(now, MINIMUM_TINYBARS_COST);

        final var selector = input.getInt(0);
        try {
            final var answer = answerGiven(selector);
            return Pair.of(costInGas, answer);
        } catch (final InvalidTransactionException e) {
            if (e.isReverting()) {
                frame.setRevertReason(e.getRevertReason());
                frame.setState(MessageFrame.State.REVERT);
            }
            return Pair.of(costInGas, null);
        }
    }

    private Bytes answerGiven(final int selector) {
        switch (selector) {
            case ABI_ID_GET_TOKEN_INFO -> {
                final var wrapper = TokenInfoPrecompile.decodeGetTokenInfo(input);
                final var tokenInfo =
                        ledgers.infoForToken(wrapper.token(), stateView.getNetworkInfo().ledgerId())
                                .orElse(null);

                validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);

                return encoder.encodeGetTokenInfo(tokenInfo);
            }
            case ABI_ID_GET_FUNGIBLE_TOKEN_INFO -> {
                final var wrapper = FungibleTokenInfoPrecompile.decodeGetFungibleTokenInfo(input);
                final var tokenInfo =
                        ledgers.infoForToken(wrapper.token(), stateView.getNetworkInfo().ledgerId())
                                .orElse(null);

                validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);

                return encoder.encodeGetFungibleTokenInfo(tokenInfo);
            }
            case ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO -> {
                final var wrapper =
                        NonFungibleTokenInfoPrecompile.decodeGetNonFungibleTokenInfo(input);
                final var tokenInfo =
                        ledgers.infoForToken(wrapper.token(), stateView.getNetworkInfo().ledgerId())
                                .orElse(null);

                validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);

                final var nftID =
                        NftID.newBuilder()
                                .setTokenID(wrapper.token())
                                .setSerialNumber(wrapper.serialNumber())
                                .build();
                final var nonFungibleTokenInfo =
                        ledgers.infoForNft(nftID, stateView.getNetworkInfo().ledgerId())
                                .orElse(null);
                validateTrueOrRevert(
                        nonFungibleTokenInfo != null,
                        ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER);

                return encoder.encodeGetNonFungibleTokenInfo(tokenInfo, nonFungibleTokenInfo);
            }
            case ABI_ID_IS_FROZEN -> {
                final var wrapper = IsFrozenPrecompile.decodeIsFrozen(input, updater::unaliased);

                validateTrueOrRevert(
                        ledgers.isTokenAddress(EntityIdUtils.asTypedEvmAddress(wrapper.token())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var isFrozen = ledgers.isFrozen(wrapper.account(), wrapper.token());
                return evmEncoder.encodeIsFrozen(isFrozen);
            }
            case ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS -> {
                final var wrapper =
                        GetTokenDefaultFreezeStatus.decodeTokenDefaultFreezeStatus(input);

                validateTrueOrRevert(
                        ledgers.isTokenAddress(EntityIdUtils.asTypedEvmAddress(wrapper.token())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var defaultFreezeStatus = ledgers.defaultFreezeStatus(wrapper.token());
                return evmEncoder.encodeGetTokenDefaultFreezeStatus(defaultFreezeStatus);
            }
            case ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS -> {
                final var wrapper = GetTokenDefaultKycStatus.decodeTokenDefaultKycStatus(input);

                validateTrueOrRevert(
                        ledgers.isTokenAddress(EntityIdUtils.asTypedEvmAddress(wrapper.token())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var defaultKycStatus = ledgers.defaultKycStatus(wrapper.token());
                return evmEncoder.encodeGetTokenDefaultKycStatus(defaultKycStatus);
            }
            case ABI_ID_IS_KYC -> {
                final var wrapper = IsKycPrecompile.decodeIsKyc(input, updater::unaliased);

                validateTrueOrRevert(
                        ledgers.isTokenAddress(EntityIdUtils.asTypedEvmAddress(wrapper.token())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var isKyc = ledgers.isKyc(wrapper.account(), wrapper.token());
                return evmEncoder.encodeIsKyc(isKyc);
            }
            case ABI_ID_GET_TOKEN_CUSTOM_FEES -> {
                final var wrapper = TokenGetCustomFeesPrecompile.decodeTokenGetCustomFees(input);
                final var customFees = ledgers.infoForTokenCustomFees(wrapper.token()).orElse(null);

                validateTrueOrRevert(customFees != null, ResponseCodeEnum.INVALID_TOKEN_ID);

                return encoder.encodeTokenGetCustomFees(customFees);
            }
            case ABI_ID_IS_TOKEN -> {
                final var wrapper = IsTokenPrecompile.decodeIsToken(input);

                validateTrueOrRevert(
                        ledgers.isTokenAddress(EntityIdUtils.asTypedEvmAddress(wrapper.token())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var isToken =
                        ledgers.isTokenAddress(EntityIdUtils.asTypedEvmAddress((wrapper.token())));
                return evmEncoder.encodeIsToken(isToken);
            }
            case ABI_ID_GET_TOKEN_TYPE -> {
                final var wrapper = GetTokenTypePrecompile.decodeGetTokenType(input);

                validateTrueOrRevert(
                        ledgers.isTokenAddress(EntityIdUtils.asTypedEvmAddress(wrapper.token())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var tokenType = ledgers.typeOf(wrapper.token());
                return evmEncoder.encodeGetTokenType(tokenType.ordinal());
            }
            case ABI_ID_GET_TOKEN_EXPIRY_INFO -> {
                final var wrapper = GetTokenExpiryInfoPrecompile.decodeGetTokenExpiryInfo(input);
                final var tokenInfo =
                        ledgers.infoForToken(wrapper.token(), stateView.getNetworkInfo().ledgerId())
                                .orElse(null);

                validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);
                Objects.requireNonNull(tokenInfo);

                final var expiryInfo =
                        new TokenExpiryWrapper(
                                tokenInfo.getExpiry().getSeconds(),
                                tokenInfo.getAutoRenewAccount(),
                                tokenInfo.getAutoRenewPeriod().getSeconds());

                return encoder.encodeGetTokenExpiryInfo(expiryInfo);
            }
            case ABI_ID_GET_TOKEN_KEY -> {
                final var wrapper = GetTokenKeyPrecompile.decodeGetTokenKey(input);

                validateTrueOrRevert(
                        ledgers.isTokenAddress(EntityIdUtils.asTypedEvmAddress(wrapper.tokenID())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var key = ledgers.keyOf(wrapper.tokenID(), wrapper.tokenKeyType());
                return encoder.encodeGetTokenKey(buildKeyValueWrapper(key));
            }
                // Only view functions can be used inside a ContractCallLocal
            default -> throw new InvalidTransactionException(NOT_SUPPORTED);
        }
    }
}
