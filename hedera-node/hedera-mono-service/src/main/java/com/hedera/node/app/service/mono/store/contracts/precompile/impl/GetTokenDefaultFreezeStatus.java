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

import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenDefaultFreezeStatusWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenDefaultFreezeStatus;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class GetTokenDefaultFreezeStatus extends AbstractReadOnlyPrecompile
        implements EvmGetTokenDefaultFreezeStatus {

    private GetTokenDefaultFreezeStatusWrapper<TokenID> defaultFreezeStatusWrapper;

    public GetTokenDefaultFreezeStatus(
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final PrecompilePricingUtils pricingUtils) {
        super(null, syntheticTxnFactory, ledgers, encoder, evmEncoder, pricingUtils);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        defaultFreezeStatusWrapper = decodeTokenDefaultFreezeStatus(input);
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        Objects.requireNonNull(
                defaultFreezeStatusWrapper,
                "`body` method should be called before `getSuccessResultsFor`");

        final var defaultFreezeStatus =
                ledgers.defaultFreezeStatus(defaultFreezeStatusWrapper.token());
        return encoder.encodeGetTokenDefaultFreezeStatus(defaultFreezeStatus);
    }

    public static GetTokenDefaultFreezeStatusWrapper<TokenID> decodeTokenDefaultFreezeStatus(
            final Bytes input) {
        final var rawGetTokenDefaultFreezeStatusWrapper =
                EvmGetTokenDefaultFreezeStatus.decodeTokenDefaultFreezeStatus(input);
        return new GetTokenDefaultFreezeStatusWrapper<>(
                convertAddressBytesToTokenID(rawGetTokenDefaultFreezeStatusWrapper.token()));
    }
}
