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
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BalanceOfWrapper;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class BalanceOfPrecompile extends AbstractReadOnlyPrecompile {
    private static final Function BALANCE_OF_TOKEN_FUNCTION =
            new Function("balanceOf(address)", INT);
    private static final Bytes BALANCE_OF_TOKEN_SELECTOR =
            Bytes.wrap(BALANCE_OF_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> BALANCE_OF_TOKEN_DECODER = TypeFactory.create(BYTES32);

    private BalanceOfWrapper balanceWrapper;

    public BalanceOfPrecompile(
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
        final var nestedInput = input.slice(24);
        balanceWrapper = decodeBalanceOf(nestedInput, aliasResolver);
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        Objects.requireNonNull(
                balanceWrapper, "`body` method should be called before `getSuccessResultsFor`");

        final var balance = ledgers.balanceOf(balanceWrapper.accountId(), tokenId);
        return encoder.encodeBalance(balance);
    }

    public static BalanceOfWrapper decodeBalanceOf(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, BALANCE_OF_TOKEN_SELECTOR, BALANCE_OF_TOKEN_DECODER);

        final var account =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(0), aliasResolver);

        return new BalanceOfWrapper(account);
    }
}
