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
package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.services.contracts.ParsingConstants.INT;
import static com.hedera.services.contracts.ParsingConstants.UINT256;
import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.OwnerOfAndTokenURIWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class OwnerOfPrecompile extends AbstractReadOnlyPrecompile {
    private static final Function OWNER_OF_NFT_FUNCTION = new Function("ownerOf(uint256)", INT);
    private static final Bytes OWNER_OF_NFT_SELECTOR = Bytes.wrap(OWNER_OF_NFT_FUNCTION.selector());
    private static final ABIType<Tuple> OWNER_OF_NFT_DECODER = TypeFactory.create(UINT256);
    private NftId nftId;

    public OwnerOfPrecompile(
            final TokenID tokenId,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, pricingUtils);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var wrapper = decodeOwnerOf(input.slice(24));
        nftId =
                new NftId(
                        tokenId.getShardNum(),
                        tokenId.getRealmNum(),
                        tokenId.getTokenNum(),
                        wrapper.serialNo());
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        Objects.requireNonNull(
                nftId, "`body` method should be called before `getSuccessResultsFor`");

        final var nftsLedger = ledgers.nfts();
        validateTrueOrRevert(nftsLedger.contains(nftId), INVALID_TOKEN_NFT_SERIAL_NUMBER);
        final var owner = ledgers.ownerOf(nftId);
        final var priorityAddress = ledgers.canonicalAddress(owner);
        return encoder.encodeOwner(priorityAddress);
    }

    public static OwnerOfAndTokenURIWrapper decodeOwnerOf(final Bytes input) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, OWNER_OF_NFT_SELECTOR, OWNER_OF_NFT_DECODER);

        final var tokenId = (BigInteger) decodedArguments.get(0);

        return new OwnerOfAndTokenURIWrapper(tokenId.longValueExact());
    }
}
