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

import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_KEY;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_TYPE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_IS_FROZEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_IS_KYC;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_IS_TOKEN;
import static com.hedera.services.store.contracts.precompile.utils.PrecompileUtils.buildKeyValueWrapper;
import static com.hedera.services.utils.MiscUtils.asSecondsTimestamp;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.services.store.contracts.precompile.impl.FungibleTokenInfoPrecompile;
import com.hedera.services.store.contracts.precompile.impl.GetTokenDefaultFreezeStatus;
import com.hedera.services.store.contracts.precompile.impl.GetTokenDefaultKycStatus;
import com.hedera.services.store.contracts.precompile.impl.GetTokenExpiryInfoPrecompile;
import com.hedera.services.store.contracts.precompile.impl.GetTokenKeyPrecompile;
import com.hedera.services.store.contracts.precompile.impl.GetTokenTypePrecompile;
import com.hedera.services.store.contracts.precompile.impl.IsFrozenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.IsKycPrecompile;
import com.hedera.services.store.contracts.precompile.impl.IsTokenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.NonFungibleTokenInfoPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenGetCustomFeesPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenInfoPrecompile;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class ViewExecutor {
    public static final long MINIMUM_TINYBARS_COST = 100;

    private final Bytes input;
    private final MessageFrame frame;
    private final EncodingFacade encoder;
    private final ViewGasCalculator gasCalculator;
    private final StateView stateView;
    private final WorldLedgers ledgers;

    public ViewExecutor(
            final Bytes input,
            final MessageFrame frame,
            final EncodingFacade encoder,
            final ViewGasCalculator gasCalculator,
            final StateView stateView,
            final WorldLedgers ledgers) {
        this.input = input;
        this.frame = frame;
        this.encoder = encoder;
        this.gasCalculator = gasCalculator;
        this.stateView = stateView;
        this.ledgers = ledgers;
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
                final var tokenInfo = stateView.infoForToken(wrapper.tokenID()).orElse(null);

                validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);

                return encoder.encodeGetTokenInfo(tokenInfo);
            }
            case ABI_ID_GET_FUNGIBLE_TOKEN_INFO -> {
                final var wrapper = FungibleTokenInfoPrecompile.decodeGetFungibleTokenInfo(input);
                final var tokenInfo = stateView.infoForToken(wrapper.tokenID()).orElse(null);

                validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);

                return encoder.encodeGetFungibleTokenInfo(tokenInfo);
            }
            case ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO -> {
                final var wrapper =
                        NonFungibleTokenInfoPrecompile.decodeGetNonFungibleTokenInfo(input);
                final var tokenInfo = stateView.infoForToken(wrapper.tokenID()).orElse(null);

                validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);

                final var nftID =
                        NftID.newBuilder()
                                .setTokenID(wrapper.tokenID())
                                .setSerialNumber(wrapper.serialNumber())
                                .build();
                final var nonFungibleTokenInfo = stateView.infoForNft(nftID).orElse(null);
                validateTrueOrRevert(
                        nonFungibleTokenInfo != null,
                        ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER);

                return encoder.encodeGetNonFungibleTokenInfo(tokenInfo, nonFungibleTokenInfo);
            }
            case ABI_ID_IS_FROZEN -> {
                final var wrapper = IsFrozenPrecompile.decodeIsFrozen(input, a -> a);
                final var isFrozen = ledgers.isFrozen(wrapper.account(), wrapper.token());

                return encoder.encodeIsFrozen(isFrozen);
            }
            case ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS -> {
                final var wrapper =
                        GetTokenDefaultFreezeStatus.decodeTokenDefaultFreezeStatus(input);
                final var defaultFreezeStatus = ledgers.defaultFreezeStatus(wrapper.tokenID());

                return encoder.encodeGetTokenDefaultFreezeStatus(defaultFreezeStatus);
            }
            case ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS -> {
                final var wrapper = GetTokenDefaultKycStatus.decodeTokenDefaultKycStatus(input);
                final var defaultKycStatus = ledgers.defaultKycStatus(wrapper.tokenID());

                return encoder.encodeGetTokenDefaultKycStatus(defaultKycStatus);
            }
            case ABI_ID_IS_KYC -> {
                final var wrapper = IsKycPrecompile.decodeIsKyc(input, a -> a);
                final var isKyc = ledgers.isKyc(wrapper.account(), wrapper.token());

                return encoder.encodeIsKyc(isKyc);
            }
            case ABI_ID_GET_TOKEN_CUSTOM_FEES -> {
                final var wrapper = TokenGetCustomFeesPrecompile.decodeTokenGetCustomFees(input);
                validateTrueOrRevert(
                        stateView.tokenExists(wrapper.tokenID()),
                        ResponseCodeEnum.INVALID_TOKEN_ID);
                final var customFees = stateView.tokenCustomFees(wrapper.tokenID());
                return encoder.encodeTokenGetCustomFees(customFees);
            }
            case ABI_ID_IS_TOKEN -> {
                final var wrapper = IsTokenPrecompile.decodeIsToken(input);
                final var isToken = stateView.tokenExists(wrapper.tokenID());
                return encoder.encodeIsToken(isToken);
            }
            case ABI_ID_GET_TOKEN_TYPE -> {
                final var wrapper = GetTokenTypePrecompile.decodeGetTokenType(input);
                validateTrueOrRevert(
                        stateView.tokenExists(wrapper.tokenID()),
                        ResponseCodeEnum.INVALID_TOKEN_ID);
                final var token = stateView.tokens().get(EntityNum.fromTokenId(wrapper.tokenID()));
                return encoder.encodeGetTokenType(token.tokenType().ordinal());
            }
            case ABI_ID_GET_TOKEN_EXPIRY_INFO -> {
                final var wrapper = GetTokenExpiryInfoPrecompile.decodeGetTokenExpiryInfo(input);
                validateTrueOrRevert(
                        stateView.tokenExists(wrapper.tokenID()),
                        ResponseCodeEnum.INVALID_TOKEN_ID);
                final var tokenInfo = stateView.infoForToken(wrapper.tokenID()).orElse(null);

                if (tokenInfo == null) {
                    throw new InvalidTransactionException(ResponseCodeEnum.INVALID_TOKEN_ID, true);
                }

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
                        stateView.tokenExists(wrapper.tokenID()),
                        ResponseCodeEnum.INVALID_TOKEN_ID);
                JKey key = (JKey) ledgers.tokens().get(wrapper.tokenID(), wrapper.tokenKeyType());
                return encoder.encodeGetTokenKey(buildKeyValueWrapper(key));
            }
                // Only view functions can be used inside a ContractCallLocal
            default -> throw new InvalidTransactionException(NOT_SUPPORTED);
        }
    }
}
