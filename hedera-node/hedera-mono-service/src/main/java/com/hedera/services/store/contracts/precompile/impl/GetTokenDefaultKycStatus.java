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

import static com.hedera.services.contracts.ParsingConstants.BYTES32;
import static com.hedera.services.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.GetTokenDefaultKycStatusWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class GetTokenDefaultKycStatus extends AbstractReadOnlyPrecompile {
    private static final Function GET_TOKEN_DEFAULT_KYC_STATUS_FUNCTION =
            new Function("getTokenDefaultKycStatus(address)", INT);
    private static final Bytes GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR =
            Bytes.wrap(GET_TOKEN_DEFAULT_KYC_STATUS_FUNCTION.selector());
    private static final ABIType<Tuple> GET_TOKEN_DEFAULT_KYC_STATUS_DECODER =
            TypeFactory.create(BYTES32);
    private GetTokenDefaultKycStatusWrapper defaultKycStatusWrapper;

    public GetTokenDefaultKycStatus(
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        super(null, syntheticTxnFactory, ledgers, encoder, pricingUtils);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        defaultKycStatusWrapper = decodeTokenDefaultKycStatus(input);
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        Objects.requireNonNull(
                defaultKycStatusWrapper,
                "`body` method should be called before `getSuccessResultsFor`");

        final var defaultKycStatus = ledgers.defaultKycStatus(defaultKycStatusWrapper.tokenID());
        return encoder.encodeGetTokenDefaultKycStatus(defaultKycStatus);
    }

    public static GetTokenDefaultKycStatusWrapper decodeTokenDefaultKycStatus(final Bytes input) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input,
                        GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR,
                        GET_TOKEN_DEFAULT_KYC_STATUS_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));

        return new GetTokenDefaultKycStatusWrapper(tokenID);
    }
}
