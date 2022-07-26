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
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.services.utils.MiscUtils.asSecondsTimestamp;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
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
    private final DecodingFacade decoder;
    private final ViewGasCalculator gasCalculator;
    private final StateView stateView;
    private final WorldLedgers ledgers;

    public ViewExecutor(
            final Bytes input,
            final MessageFrame frame,
            final EncodingFacade encoder,
            final DecodingFacade decoder,
            final ViewGasCalculator gasCalculator,
            final StateView stateView,
            final WorldLedgers ledgers) {
        this.input = input;
        this.frame = frame;
        this.encoder = encoder;
        this.decoder = decoder;
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
        if (selector == ABI_ID_GET_TOKEN_INFO) {
            final var wrapper = decoder.decodeGetTokenInfo(input);
            final var tokenInfo = stateView.infoForToken(wrapper.tokenID()).orElse(null);

            validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);

            return encoder.encodeGetTokenInfo(tokenInfo);
        } else if (selector == ABI_ID_GET_FUNGIBLE_TOKEN_INFO) {
            final var wrapper = decoder.decodeGetFungibleTokenInfo(input);
            final var tokenInfo = stateView.infoForToken(wrapper.tokenID()).orElse(null);

            validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);

            return encoder.encodeGetFungibleTokenInfo(tokenInfo);
        } else if (selector == ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO) {
            final var wrapper = decoder.decodeGetNonFungibleTokenInfo(input);
            final var tokenInfo = stateView.infoForToken(wrapper.tokenID()).orElse(null);

            validateTrueOrRevert(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);

            final var nftID =
                    NftID.newBuilder()
                            .setTokenID(wrapper.tokenID())
                            .setSerialNumber(wrapper.serialNumber())
                            .build();
            final var nonFungibleTokenInfo = stateView.infoForNft(nftID).orElse(null);
            validateTrueOrRevert(
                    nonFungibleTokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER);

            return encoder.encodeGetNonFungibleTokenInfo(tokenInfo, nonFungibleTokenInfo);
        } else if (selector == ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS) {
            final var wrapper = decoder.decodeTokenDefaultFreezeStatus(input);
            final var defaultFreezeStatus =
                ledgers.defaultFreezeStatus(wrapper.tokenID());

            return encoder.encodeGetTokenDefaultFreezeStatus(defaultFreezeStatus);
        } else if (selector == ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS) {
            final var wrapper = decoder.decodeTokenDefaultKycStatus(input);
            final var defaultKycStatus = ledgers.defaultKycStatus(wrapper.tokenID());

            return encoder.encodeGetTokenDefaultKycStatus(defaultKycStatus);
        } else {
            // Only view functions can be used inside a ContractCallLocal
            throw new InvalidTransactionException(NOT_SUPPORTED);
        }
    }
}
