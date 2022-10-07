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
import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.APPROVE;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.SetApprovalForAllWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

public class SetApprovalForAllPrecompile extends AbstractWritePrecompile {
    private static final Function ERC_SET_APPROVAL_FOR_ALL =
            new Function("setApprovalForAll(address,bool)");
    private static final Bytes ERC_SET_APPROVAL_FOR_ALL_SELECTOR =
            Bytes.wrap(ERC_SET_APPROVAL_FOR_ALL.selector());
    private static final ABIType<Tuple> ERC_SET_APPROVAL_FOR_ALL_DECODER =
            TypeFactory.create("(bytes32,bool)");
    private static final Function HAPI_SET_APPROVAL_FOR_ALL =
            new Function("setApprovalForAll(address,address,bool)", INT);
    private static final Bytes HAPI_SET_APPROVAL_FOR_ALL_SELECTOR =
            Bytes.wrap(HAPI_SET_APPROVAL_FOR_ALL.selector());
    private static final ABIType<Tuple> HAPI_SET_APPROVAL_FOR_ALL_DECODER =
            TypeFactory.create("(bytes32,bytes32,bool)");
    private final TokenID tokenId;
    private final Address senderAddress;
    private final StateView currentView;
    private SetApprovalForAllWrapper setApprovalForAllWrapper;

    public SetApprovalForAllPrecompile(
            final TokenID tokenId,
            final WorldLedgers ledgers,
            final StateView currentView,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final Address senderAddress) {
        super(ledgers, sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
        this.tokenId = tokenId;
        this.senderAddress = senderAddress;
        this.currentView = currentView;
    }

    public SetApprovalForAllPrecompile(
            final WorldLedgers ledgers,
            final StateView currentView,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final Address senderAddress) {
        this(
                null,
                ledgers,
                currentView,
                sideEffects,
                syntheticTxnFactory,
                infrastructureFactory,
                pricingUtils,
                senderAddress);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var nestedInput = tokenId == null ? input : input.slice(24);
        setApprovalForAllWrapper = decodeSetApprovalForAll(nestedInput, tokenId, aliasResolver);
        transactionBody =
                syntheticTxnFactory.createApproveAllowanceForAllNFT(setApprovalForAllWrapper);
        return transactionBody;
    }

    @Override
    public void run(MessageFrame frame) {
        Objects.requireNonNull(
                setApprovalForAllWrapper, "`body` method should be called before `run`");
        /* --- Build the necessary infrastructure to execute the transaction --- */
        final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
        final var tokenStore =
                infrastructureFactory.newTokenStore(
                        accountStore,
                        sideEffects,
                        ledgers.tokens(),
                        ledgers.nfts(),
                        ledgers.tokenRels());
        final var payerAccount =
                accountStore.loadAccount(
                        Id.fromGrpcAccount(EntityIdUtils.accountIdFromEvmAddress(senderAddress)));

        final var approveAllowanceChecks = infrastructureFactory.newApproveAllowanceChecks();

        final var status =
                approveAllowanceChecks.allowancesValidation(
                        transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
                        transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
                        transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
                        payerAccount,
                        currentView);
        validateTrueOrRevert(status == OK, status);

        /* --- Execute the transaction and capture its results --- */
        final var approveAllowanceLogic =
                infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore);
        approveAllowanceLogic.approveAllowance(
                transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
                transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
                transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
                EntityIdUtils.accountIdFromEvmAddress(frame.getSenderAddress()));

        final var tokenAddress = asTypedEvmAddress(setApprovalForAllWrapper.tokenId());
        frame.addLog(getLogForSetApprovalForAll(tokenAddress));
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime) {
        return pricingUtils.getMinimumPriceInTinybars(APPROVE, consensusTime);
    }

    private Log getLogForSetApprovalForAll(final Address logger) {
        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_FOR_ALL_EVENT)
                .forIndexedArgument(ledgers.canonicalAddress(senderAddress))
                .forIndexedArgument(
                        ledgers.canonicalAddress(asTypedEvmAddress(setApprovalForAllWrapper.to())))
                .forDataItem(setApprovalForAllWrapper.approved())
                .build();
    }

    public static SetApprovalForAllWrapper decodeSetApprovalForAll(
            final Bytes input,
            final TokenID impliedTokenId,
            final UnaryOperator<byte[]> aliasResolver) {
        final var offset = impliedTokenId == null ? 1 : 0;
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input,
                        offset == 0
                                ? ERC_SET_APPROVAL_FOR_ALL_SELECTOR
                                : HAPI_SET_APPROVAL_FOR_ALL_SELECTOR,
                        offset == 0
                                ? ERC_SET_APPROVAL_FOR_ALL_DECODER
                                : HAPI_SET_APPROVAL_FOR_ALL_DECODER);
        final var tokenId =
                offset == 0
                        ? impliedTokenId
                        : convertAddressBytesToTokenID(decodedArguments.get(0));

        final var to =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(offset), aliasResolver);
        final var approved = (boolean) decodedArguments.get(offset + 1);

        return new SetApprovalForAllWrapper(tokenId, to, approved);
    }
}
