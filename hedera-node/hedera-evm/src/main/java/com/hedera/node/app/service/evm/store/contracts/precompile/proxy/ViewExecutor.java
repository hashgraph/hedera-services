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

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenExpiryInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmFungibleTokenInfoPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenDefaultFreezeStatus;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenDefaultKycStatus;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenExpiryInfoPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenKeyPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmIsFrozenPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmIsKycPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmIsTokenPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmNonFungibleTokenInfoPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmTokenGetCustomFeesPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmTokenInfoPrecompile;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Objects;

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_KEY;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_TYPE;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_FROZEN;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_KYC;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_TOKEN;
import static com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenTypePrecompile.decodeGetTokenType;
import static com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectViewExecutor.asSecondsTimestamp;
import static com.hedera.node.app.service.evm.utils.EntityIdUtil.addressFromBytes;
import static com.hedera.node.app.service.evm.utils.EntityIdUtil.asTypedEvmAddress;
import static com.hedera.node.app.service.evm.utils.EntityIdUtil.tokenIdFromEvmAddress;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrueOrRevert;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

public class ViewExecutor {

    public static final long MINIMUM_TINYBARS_COST = 100;

    private final Bytes input;
    private final MessageFrame frame;
    private final EvmEncodingFacade evmEncoder;
    private final ViewGasCalculator gasCalculator;
    private final TokenAccessor tokenAccessor;
    private final ByteString ledgerId;

    public ViewExecutor(
            final Bytes input,
            final MessageFrame frame,
            final EvmEncodingFacade evmEncoder,
            final ViewGasCalculator gasCalculator,
            final TokenAccessor tokenAccessor,
            final ByteString ledgerId) {
        this.input = input;
        this.frame = frame;
        this.evmEncoder = evmEncoder;
        this.gasCalculator = gasCalculator;
        this.ledgerId = ledgerId;
        this.tokenAccessor = tokenAccessor;
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
                final var wrapper = EvmTokenInfoPrecompile.decodeGetTokenInfo(input);
                final var tokenInfo =
                        tokenAccessor.evmInfoForToken(
                                        addressFromBytes(wrapper.token()), ledgerId)
                                .orElse(null);

                validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);

                return evmEncoder.encodeGetTokenInfo(tokenInfo);
            }
            case ABI_ID_GET_FUNGIBLE_TOKEN_INFO -> {
                final var wrapper = EvmFungibleTokenInfoPrecompile.decodeGetFungibleTokenInfo(input);
                final var tokenInfo =
                        tokenAccessor.evmInfoForToken(
                                        addressFromBytes(wrapper.token()), ledgerId)
                                .orElse(null);

                validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);

                return evmEncoder.encodeGetFungibleTokenInfo(tokenInfo);
            }
            case ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO -> {
                final var wrapper =
                        EvmNonFungibleTokenInfoPrecompile.decodeGetNonFungibleTokenInfo(input);
                final var tokenInfo =
                        tokenAccessor.evmInfoForToken(
                                        addressFromBytes(wrapper.token()), ledgerId)
                                .orElse(null);

                validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);
                final var nftID =
                        NftID.newBuilder()
                                .setTokenID(tokenIdFromEvmAddress(wrapper.token()))
                                .setSerialNumber(wrapper.serialNumber())
                                .build();
                final var nftAddress = asTypedEvmAddress(nftID.getTokenID());
                final var nftSerialNo = nftID.getSerialNumber();
                final var nonFungibleTokenInfo =
                        tokenAccessor.evmNftInfo(nftAddress,nftSerialNo, ledgerId)
                                .orElse(null);
                validateTrueOrRevert(
                        nonFungibleTokenInfo != null,
                        ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER);

                return evmEncoder.encodeGetNonFungibleTokenInfo(tokenInfo, nonFungibleTokenInfo);
            }
            case ABI_ID_IS_FROZEN -> {
                final var wrapper = EvmIsFrozenPrecompile.decodeIsFrozen(input);

                validateTrueOrRevert(
                        tokenAccessor.isTokenAddress(addressFromBytes(wrapper.token())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var isFrozen = tokenAccessor.isFrozen(addressFromBytes(wrapper.account()),
                        addressFromBytes(wrapper.token()));
                return evmEncoder.encodeIsFrozen(isFrozen);
            }
            case ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS -> {
                final var wrapper =
                        EvmGetTokenDefaultFreezeStatus.decodeTokenDefaultFreezeStatus(input);

                validateTrueOrRevert(
                        tokenAccessor.isTokenAddress(addressFromBytes(wrapper.token())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var defaultFreezeStatus = tokenAccessor.defaultFreezeStatus(addressFromBytes(wrapper.token()));
                return evmEncoder.encodeGetTokenDefaultFreezeStatus(defaultFreezeStatus);
            }
            case ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS -> {
                final var wrapper = EvmGetTokenDefaultKycStatus.decodeTokenDefaultKycStatus(input);

                validateTrueOrRevert(
                        tokenAccessor.isTokenAddress(addressFromBytes(wrapper.token())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var defaultKycStatus = tokenAccessor.defaultKycStatus(addressFromBytes(wrapper.token()));
                return evmEncoder.encodeGetTokenDefaultKycStatus(defaultKycStatus);
            }
            case ABI_ID_IS_KYC -> {
                //TODO
                final var wrapper = EvmIsKycPrecompile.decodeIsKyc(input);

                validateTrueOrRevert(
                        tokenAccessor.isTokenAddress(addressFromBytes(wrapper.token())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var isKyc = tokenAccessor.isKyc(addressFromBytes(wrapper.account()),
                        addressFromBytes(wrapper.token()));
                return evmEncoder.encodeIsKyc(isKyc);
            }
            case ABI_ID_GET_TOKEN_CUSTOM_FEES -> {
                final var wrapper = EvmTokenGetCustomFeesPrecompile.decodeTokenGetCustomFees(input);
                final var customFees =
                        tokenAccessor.infoForTokenCustomFees(addressFromBytes(wrapper.token())).orElse(null);

                validateTrueOrRevert(customFees != null, ResponseCodeEnum.INVALID_TOKEN_ID);

                return evmEncoder.encodeTokenGetCustomFees(customFees);
            }
            case ABI_ID_IS_TOKEN -> {
                final var wrapper = EvmIsTokenPrecompile.decodeIsToken(input);

                validateTrueOrRevert(
                        tokenAccessor.isTokenAddress(addressFromBytes(wrapper.token())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var isToken =
                        tokenAccessor.isTokenAddress(addressFromBytes((wrapper.token())));
                return evmEncoder.encodeIsToken(isToken);
            }
            case ABI_ID_GET_TOKEN_TYPE -> {
                final var wrapper = decodeGetTokenType(input);

                validateTrueOrRevert(
                        tokenAccessor.isTokenAddress(addressFromBytes(wrapper.token())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var tokenType = tokenAccessor.typeOf(addressFromBytes(wrapper.token()));
                return evmEncoder.encodeGetTokenType(tokenType.ordinal());
            }
            case ABI_ID_GET_TOKEN_EXPIRY_INFO -> {
                final var wrapper = EvmGetTokenExpiryInfoPrecompile.decodeGetTokenExpiryInfo(input);
                final var tokenInfo =
                        tokenAccessor.infoForToken(addressFromBytes(wrapper.token()), ledgerId)
                                .orElse(null);

                validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);
                Objects.requireNonNull(tokenInfo);
                final var expiryInfo =
                        new TokenExpiryInfo(
                                tokenInfo.getExpiry().getSeconds(),
                                asTypedEvmAddress(tokenInfo.getAutoRenewAccount()),
                                tokenInfo.getAutoRenewPeriod().getSeconds());

                return evmEncoder.encodeGetTokenExpiryInfo(expiryInfo);
            }
            case ABI_ID_GET_TOKEN_KEY -> {
                final var wrapper = EvmGetTokenKeyPrecompile.decodeGetTokenKey(input);

                validateTrueOrRevert(
                        tokenAccessor.isTokenAddress(addressFromBytes(wrapper.token())),
                        ResponseCodeEnum.INVALID_TOKEN_ID);

                final var evmKey = tokenAccessor.keyOf(addressFromBytes(wrapper.token()), wrapper.tokenKeyType());
                return evmEncoder.encodeGetTokenKey(evmKey);
            }
            // Only view functions can be used inside a ContractCallLocal
            default -> throw new InvalidTransactionException(NOT_SUPPORTED);
        }
    }
}
