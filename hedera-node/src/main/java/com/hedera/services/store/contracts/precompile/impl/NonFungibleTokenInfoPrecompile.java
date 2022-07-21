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

import com.hedera.services.config.NetworkInfo;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.contracts.precompile.utils.TokenInfoRetrievalUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class NonFungibleTokenInfoPrecompile extends AbstractTokenInfoPrecompile {
    private long serialNumber;

    public NonFungibleTokenInfoPrecompile(
            TokenID tokenId,
            SyntheticTxnFactory syntheticTxnFactory,
            WorldLedgers ledgers,
            EncodingFacade encoder,
            DecodingFacade decoder,
            PrecompilePricingUtils pricingUtils,
            NetworkInfo networkInfo) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, decoder, pricingUtils, networkInfo);
    }

    @Override
    public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        final var tokenInfoWrapper = decoder.decodeGetNonFungibleTokenInfo(input);
        tokenId = tokenInfoWrapper.tokenID();
        serialNumber = tokenInfoWrapper.serialNumber();
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        return encoder.encodeGetNonFungibleTokenInfo(
            TokenInfoRetrievalUtils.getNonFungibleTokenInfo(tokenId, serialNumber, ledgers, networkInfo));
    }
}
