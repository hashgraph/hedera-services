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

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.WIPE_FUNGIBLE;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.WipeWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class WipeFungiblePrecompile extends AbstractWipePrecompile {
    private final int functionId;
    private static final Function WIPE_TOKEN_ACCOUNT_FUNCTION =
            new Function("wipeTokenAccount(address,address,uint32)", INT);
    private static final Function WIPE_TOKEN_ACCOUNT_FUNCTION_V2 =
            new Function("wipeTokenAccount(address,address,int64)", INT);
    private static final Bytes WIPE_TOKEN_ACCOUNT_SELECTOR =
            Bytes.wrap(WIPE_TOKEN_ACCOUNT_FUNCTION.selector());
    private static final Bytes WIPE_TOKEN_ACCOUNT_SELECTOR_V2 =
            Bytes.wrap(WIPE_TOKEN_ACCOUNT_FUNCTION_V2.selector());
    private static final ABIType<Tuple> WIPE_TOKEN_ACCOUNT_DECODER =
            TypeFactory.create("(bytes32,bytes32,uint32)");
    private static final ABIType<Tuple> WIPE_TOKEN_ACCOUNT_DECODER_V2 =
            TypeFactory.create("(bytes32,bytes32,int64)");

    public WipeFungiblePrecompile(
            final WorldLedgers ledgers,
            final ContractAliases aliases,
            final EvmSigsVerifier sigsVerifier,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final int functionId) {
        super(
                ledgers,
                aliases,
                sigsVerifier,
                sideEffects,
                syntheticTxnFactory,
                infrastructureFactory,
                pricingUtils);
        this.functionId = functionId;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        wipeOp =
                switch (functionId) {
                    case AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE -> decodeWipe(
                            input, aliasResolver);
                    case AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE_V2 -> decodeWipeV2(
                            input, aliasResolver);
                    default -> null;
                };
        transactionBody = syntheticTxnFactory.createWipe(wipeOp);
        return transactionBody;
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        Objects.requireNonNull(
                wipeOp, "`body` method should be called before `getMinimumFeeInTinybars`");
        return pricingUtils.getMinimumPriceInTinybars(WIPE_FUNGIBLE, consensusTime);
    }

    public static WipeWrapper decodeWipe(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getWipeWrapper(
                input, aliasResolver, WIPE_TOKEN_ACCOUNT_SELECTOR, WIPE_TOKEN_ACCOUNT_DECODER);
    }

    public static WipeWrapper decodeWipeV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getWipeWrapper(
                input,
                aliasResolver,
                WIPE_TOKEN_ACCOUNT_SELECTOR_V2,
                WIPE_TOKEN_ACCOUNT_DECODER_V2);
    }

    private static WipeWrapper getWipeWrapper(
            final Bytes input,
            final UnaryOperator<byte[]> aliasResolver,
            final Bytes wipeTokenAccountSelector,
            final ABIType<Tuple> wipeTokenAccountDecoder) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, wipeTokenAccountSelector, wipeTokenAccountDecoder);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var accountID =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);
        final var fungibleAmount = (long) decodedArguments.get(2);

        return WipeWrapper.forFungible(tokenID, accountID, fungibleAmount);
    }
}
