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
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.BURN_FUNGIBLE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.BURN_NFT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class BurnPrecompile extends AbstractWritePrecompile {
    private static final List<Long> NO_SERIAL_NOS = Collections.emptyList();
    private static final String BURN = String.format(FAILURE_MESSAGE, "burn");
    private static final Function BURN_TOKEN_FUNCTION =
            new Function("burnToken(address,uint64,int64[])", INT);
    private static final Bytes BURN_TOKEN_SELECTOR = Bytes.wrap(BURN_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> BURN_TOKEN_DECODER =
            TypeFactory.create("(bytes32,int64,int64[])");
    private final EncodingFacade encoder;
    private final ContractAliases aliases;
    private final EvmSigsVerifier sigsVerifier;
    private BurnWrapper burnOp;

    public BurnPrecompile(
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final ContractAliases aliases,
            final EvmSigsVerifier sigsVerifier,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils) {
        super(ledgers, sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
        this.encoder = encoder;
        this.aliases = aliases;
        this.sigsVerifier = sigsVerifier;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        burnOp = decodeBurn(input);
        transactionBody = syntheticTxnFactory.createBurn(burnOp);
        return transactionBody;
    }

    @Override
    public void run(final MessageFrame frame) {
        Objects.requireNonNull(burnOp, "`body` method should be called before `run`");

        /* --- Check required signatures --- */
        final var tokenId = Id.fromGrpcToken(burnOp.tokenType());
        final var hasRequiredSigs =
                KeyActivationUtils.validateKey(
                        frame,
                        tokenId.asEvmAddress(),
                        sigsVerifier::hasActiveSupplyKey,
                        ledgers,
                        aliases);
        validateTrue(hasRequiredSigs, INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE, BURN);

        /* --- Build the necessary infrastructure to execute the transaction --- */
        final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
        final var tokenStore =
                infrastructureFactory.newTokenStore(
                        accountStore,
                        sideEffects,
                        ledgers.tokens(),
                        ledgers.nfts(),
                        ledgers.tokenRels());
        final var burnLogic = infrastructureFactory.newBurnLogic(accountStore, tokenStore);
        final var validity = burnLogic.validateSyntax(transactionBody.build());
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction and capture its results --- */
        if (burnOp.type() == NON_FUNGIBLE_UNIQUE) {
            final var targetSerialNos = burnOp.serialNos();
            burnLogic.burn(tokenId, 0, targetSerialNos);
        } else {
            burnLogic.burn(tokenId, burnOp.amount(), NO_SERIAL_NOS);
        }
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        Objects.requireNonNull(
                burnOp, "`body` method should be called before `getMinimumFeeInTinybars`");
        return pricingUtils.getMinimumPriceInTinybars(
                (burnOp.type() == NON_FUNGIBLE_UNIQUE) ? BURN_NFT : BURN_FUNGIBLE, consensusTime);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        final var receiptBuilder = childRecord.getReceiptBuilder();
        validateTrue(receiptBuilder != null, FAIL_INVALID);
        return encoder.encodeBurnSuccess(childRecord.getReceiptBuilder().getNewTotalSupply());
    }

    @Override
    public Bytes getFailureResultFor(final ResponseCodeEnum status) {
        return encoder.encodeBurnFailure(status);
    }

    public static BurnWrapper decodeBurn(final Bytes input) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, BURN_TOKEN_SELECTOR, BURN_TOKEN_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var fungibleAmount = (long) decodedArguments.get(1);
        final var serialNumbers = (long[]) decodedArguments.get(2);

        if (fungibleAmount > 0) {
            return BurnWrapper.forFungible(tokenID, fungibleAmount);
        } else {
            return BurnWrapper.forNonFungible(
                    tokenID, Arrays.stream(serialNumbers).boxed().toList());
        }
    }
}
