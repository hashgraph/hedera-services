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
package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmNonFungibleTokenInfoPrecompile;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class NonFungibleTokenInfoPrecompile extends AbstractTokenInfoPrecompile
        implements EvmNonFungibleTokenInfoPrecompile {

    private long serialNumber;

    public NonFungibleTokenInfoPrecompile(
            final TokenID tokenId,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final PrecompilePricingUtils pricingUtils,
            final StateView stateView) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, evmEncoder, pricingUtils, stateView);
    }

    @Override
    public Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var tokenInfoWrapper = decodeGetNonFungibleTokenInfo(input);
        tokenId = tokenInfoWrapper.token();
        serialNumber = tokenInfoWrapper.serialNumber();
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        final var tokenInfo =
                ledgers.infoForToken(tokenId, stateView.getNetworkInfo().ledgerId()).orElse(null);
        validateTrue(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);

        final var nftID =
                NftID.newBuilder().setTokenID(tokenId).setSerialNumber(serialNumber).build();
        final var nonFungibleTokenInfo =
                ledgers.infoForNft(nftID, stateView.getNetworkInfo().ledgerId()).orElse(null);
        validateTrue(
                nonFungibleTokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER);

        return encoder.encodeGetNonFungibleTokenInfo(tokenInfo, nonFungibleTokenInfo);
    }

    public static TokenInfoWrapper<TokenID> decodeGetNonFungibleTokenInfo(final Bytes input) {
        final var rawTokenInfoWrapper =
                EvmNonFungibleTokenInfoPrecompile.decodeGetNonFungibleTokenInfo(input);
        return TokenInfoWrapper.forNonFungibleToken(
                convertAddressBytesToTokenID(rawTokenInfoWrapper.token()),
                rawTokenInfoWrapper.serialNumber());
    }
}
