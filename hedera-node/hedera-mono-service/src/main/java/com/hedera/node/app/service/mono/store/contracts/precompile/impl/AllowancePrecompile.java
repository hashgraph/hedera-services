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

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_TRIO_RAW_TYPE;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrueOrRevert;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.tokenIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenAllowanceWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmAllowancePrecompile;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class AllowancePrecompile extends AbstractReadOnlyPrecompile
        implements EvmAllowancePrecompile {

    private static final Function HAPI_ALLOWANCE_FUNCTION =
            new Function("allowance(address,address,address)", "(int,int)");
    private static final Bytes HAPI_ALLOWANCE_SELECTOR =
            Bytes.wrap(HAPI_ALLOWANCE_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_ALLOWANCE_DECODER =
            TypeFactory.create(ADDRESS_TRIO_RAW_TYPE);

    private TokenAllowanceWrapper<TokenID, AccountID, AccountID> allowanceWrapper;

    public AllowancePrecompile(
            final TokenID tokenId,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final PrecompilePricingUtils pricingUtils) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, evmEncoder, pricingUtils);
    }

    public AllowancePrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        this(null, syntheticTxnFactory, ledgers, encoder, null, pricingUtils);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        allowanceWrapper = decodeTokenAllowance(input, tokenId, aliasResolver);
        return super.body(input, aliasResolver);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        Objects.requireNonNull(
                allowanceWrapper, "`body` method should be called before `getSuccessResultsFor`");

        final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger =
                ledgers.accounts();
        validateTrueOrRevert(
                accountsLedger.contains(allowanceWrapper.owner()), INVALID_ALLOWANCE_OWNER_ID);
        final var allowances =
                (Map<FcTokenAllowanceId, Long>)
                        accountsLedger.get(allowanceWrapper.owner(), FUNGIBLE_TOKEN_ALLOWANCES);
        final var fcTokenAllowanceId =
                FcTokenAllowanceId.from(allowanceWrapper.token(), allowanceWrapper.spender());
        final var value = allowances.getOrDefault(fcTokenAllowanceId, 0L);
        return tokenId == null
                ? encoder.encodeAllowance(SUCCESS.getNumber(), value)
                : evmEncoder.encodeAllowance(value);
    }

    public static TokenAllowanceWrapper<TokenID, AccountID, AccountID> decodeTokenAllowance(
            final Bytes input,
            final TokenID impliedTokenId,
            final UnaryOperator<byte[]> aliasResolver) {
        final var offset = impliedTokenId == null ? 1 : 0;
        if (offset == 1) {
            final Tuple decodedArguments =
                    decodeFunctionCall(input, HAPI_ALLOWANCE_SELECTOR, HAPI_ALLOWANCE_DECODER);

            return new TokenAllowanceWrapper<>(
                    convertAddressBytesToTokenID(decodedArguments.get(0)),
                    convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver),
                    convertLeftPaddedAddressToAccountId(decodedArguments.get(2), aliasResolver));
        } else {
            final var rawTokenAllowanceWrapper = EvmAllowancePrecompile.decodeTokenAllowance(input);
            return new TokenAllowanceWrapper<>(
                    tokenIdFromEvmAddress(rawTokenAllowanceWrapper.token()),
                    convertLeftPaddedAddressToAccountId(
                            rawTokenAllowanceWrapper.owner(), aliasResolver),
                    convertLeftPaddedAddressToAccountId(
                            rawTokenAllowanceWrapper.spender(), aliasResolver));
        }
    }
}
