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

import static com.hedera.services.contracts.ParsingConstants.ADDRESS_ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.services.contracts.ParsingConstants.ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.services.contracts.ParsingConstants.BOOL;
import static com.hedera.services.contracts.ParsingConstants.INT;
import static com.hedera.services.contracts.ParsingConstants.INT_BOOL_PAIR;
import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.APPROVE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.DELETE_NFT_APPROVE;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.util.Objects;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

public class ApprovePrecompile extends AbstractWritePrecompile {
    private static final Function ERC_TOKEN_APPROVE_FUNCTION =
            new Function("approve(address,uint256)", BOOL);
    private static final Bytes ERC_TOKEN_APPROVE_SELECTOR =
            Bytes.wrap(ERC_TOKEN_APPROVE_FUNCTION.selector());
    private static final ABIType<Tuple> ERC_TOKEN_APPROVE_DECODER =
            TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);
    private static final Function HAPI_TOKEN_APPROVE_FUNCTION =
            new Function("approve(address,address,uint256)", INT_BOOL_PAIR);
    private static final Bytes HAPI_TOKEN_APPROVE_SELECTOR =
            Bytes.wrap(HAPI_TOKEN_APPROVE_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_TOKEN_APPROVE_DECODER =
            TypeFactory.create(ADDRESS_ADDRESS_UINT256_RAW_TYPE);
    private static final Function HAPI_APPROVE_NFT_FUNCTION =
            new Function("approveNFT(address,address,uint256)", INT);
    private static final Bytes HAPI_APPROVE_NFT_SELECTOR =
            Bytes.wrap(HAPI_APPROVE_NFT_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_APPROVE_NFT_DECODER =
            TypeFactory.create(ADDRESS_ADDRESS_UINT256_RAW_TYPE);

    private final TokenID tokenId;
    private final boolean isFungible;
    private final EncodingFacade encoder;
    private final Address senderAddress;
    private final StateView currentView;
    private ApproveWrapper approveOp;
    @Nullable private EntityId operatorId;
    @Nullable private EntityId ownerId;

    public ApprovePrecompile(
            final TokenID tokenId,
            final boolean isFungible,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final StateView currentView,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final Address senderAddress) {
        super(ledgers, sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
        this.tokenId = tokenId;
        this.isFungible = isFungible;
        this.encoder = encoder;
        this.senderAddress = senderAddress;
        this.currentView = currentView;
    }

    public ApprovePrecompile(
            final boolean isFungible,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final StateView currentView,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final Address senderAddress) {
        this(
                null,
                isFungible,
                ledgers,
                encoder,
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
        operatorId = EntityId.fromAddress(senderAddress);
        approveOp = decodeTokenApprove(nestedInput, tokenId, isFungible, aliasResolver, ledgers);

        if (approveOp.isFungible()) {
            transactionBody = syntheticTxnFactory.createFungibleApproval(approveOp);
        } else {
            final var nftId =
                    NftId.fromGrpc(approveOp.tokenId(), approveOp.serialNumber().longValueExact());
            ownerId = ledgers.ownerIfPresent(nftId);
            // Per the ERC-721 spec, "The zero address indicates there is no approved address"; so
            // translate this approveAllowance into a deleteAllowance
            if (isNftApprovalRevocation()) {
                final var nominalOwnerId = ownerId != null ? ownerId : MISSING_ENTITY_ID;
                transactionBody =
                        syntheticTxnFactory.createDeleteAllowance(approveOp, nominalOwnerId);
            } else {
                transactionBody =
                        syntheticTxnFactory.createNonfungibleApproval(
                                approveOp, ownerId, operatorId);
            }
        }

        return transactionBody;
    }

    @Override
    public void run(final MessageFrame frame) {
        Objects.requireNonNull(approveOp, "`body` method should be called before `run`");

        validateTrueOrRevert(
                approveOp.isFungible() || ownerId != null, INVALID_TOKEN_NFT_SERIAL_NUMBER);
        final var grpcOperatorId = Objects.requireNonNull(operatorId).toGrpcAccountId();
        //  Per the ERC-721 spec, "Throws unless `msg.sender` is the current NFT owner, or
        //  an authorized operator of the current owner"
        if (!approveOp.isFungible()) {
            final var isApproved =
                    operatorId.equals(ownerId)
                            || ledgers.hasApprovedForAll(
                                    ownerId.toGrpcAccountId(), grpcOperatorId, approveOp.tokenId());
            validateTrueOrRevert(isApproved, SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
        }

        // --- Build the necessary infrastructure to execute the transaction ---
        final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
        final var tokenStore =
                infrastructureFactory.newTokenStore(
                        accountStore,
                        sideEffects,
                        ledgers.tokens(),
                        ledgers.nfts(),
                        ledgers.tokenRels());
        final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(grpcOperatorId));
        final var approveAllowanceChecks = infrastructureFactory.newApproveAllowanceChecks();
        final var deleteAllowanceChecks = infrastructureFactory.newDeleteAllowanceChecks();
        // --- Execute the transaction and capture its results ---
        if (isNftApprovalRevocation()) {
            final var deleteAllowanceLogic =
                    infrastructureFactory.newDeleteAllowanceLogic(accountStore, tokenStore);
            final var revocationOp = transactionBody.getCryptoDeleteAllowance();
            final var revocationWrapper = revocationOp.getNftAllowancesList();
            final var status =
                    deleteAllowanceChecks.deleteAllowancesValidation(
                            revocationWrapper, payerAccount, currentView);
            validateTrueOrRevert(status == OK, status);
            deleteAllowanceLogic.deleteAllowance(revocationWrapper, grpcOperatorId);
        } else {
            final var status =
                    approveAllowanceChecks.allowancesValidation(
                            transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
                            transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
                            transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
                            payerAccount,
                            currentView);
            validateTrueOrRevert(status == OK, status);
            final var approveAllowanceLogic =
                    infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore);
            try {
                approveAllowanceLogic.approveAllowance(
                        transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
                        transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
                        transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
                        grpcOperatorId);
            } catch (InvalidTransactionException e) {
                throw new InvalidTransactionException(e.getResponseCode(), true);
            }
        }

        final var tokenAddress = asTypedEvmAddress(approveOp.tokenId());
        if (approveOp.isFungible()) {
            frame.addLog(getLogForFungibleAdjustAllowance(tokenAddress));
        } else {
            frame.addLog(getLogForNftAdjustAllowance(tokenAddress));
        }
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime) {
        if (isNftApprovalRevocation()) {
            return pricingUtils.getMinimumPriceInTinybars(DELETE_NFT_APPROVE, consensusTime);
        } else {
            return pricingUtils.getMinimumPriceInTinybars(APPROVE, consensusTime);
        }
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        if (tokenId != null) {
            return encoder.encodeApprove(true);
        } else if (isFungible) {
            return encoder.encodeApprove(SUCCESS.getNumber(), true);
        } else {
            return encoder.encodeApproveNFT(SUCCESS.getNumber());
        }
    }

    public static ApproveWrapper decodeTokenApprove(
            final Bytes input,
            final TokenID impliedTokenId,
            final boolean isFungible,
            final UnaryOperator<byte[]> aliasResolver,
            WorldLedgers ledgers) {

        final var offset = impliedTokenId == null ? 1 : 0;
        final Tuple decodedArguments;
        final TokenID tokenId;

        if (offset == 0) {
            decodedArguments =
                    decodeFunctionCall(
                            input, ERC_TOKEN_APPROVE_SELECTOR, ERC_TOKEN_APPROVE_DECODER);
            tokenId = impliedTokenId;
        } else if (isFungible) {
            decodedArguments =
                    decodeFunctionCall(
                            input, HAPI_TOKEN_APPROVE_SELECTOR, HAPI_TOKEN_APPROVE_DECODER);
            tokenId = convertAddressBytesToTokenID(decodedArguments.get(0));
        } else {
            decodedArguments =
                    decodeFunctionCall(input, HAPI_APPROVE_NFT_SELECTOR, HAPI_APPROVE_NFT_DECODER);
            tokenId = convertAddressBytesToTokenID(decodedArguments.get(0));
        }

        final var ledgerFungible = TokenType.FUNGIBLE_COMMON.equals(ledgers.typeOf(tokenId));
        final var spender =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(offset), aliasResolver);

        if (isFungible) {
            if (!ledgerFungible) {
                throw new IllegalArgumentException("Token is not a fungible token");
            }
            final var amount = (BigInteger) decodedArguments.get(offset + 1);

            return new ApproveWrapper(tokenId, spender, amount, BigInteger.ZERO, true);
        } else {
            if (ledgerFungible) {
                throw new IllegalArgumentException("Token is not an NFT");
            }
            final var serialNumber = (BigInteger) decodedArguments.get(offset + 1);

            return new ApproveWrapper(tokenId, spender, BigInteger.ZERO, serialNumber, false);
        }
    }

    private boolean isNftApprovalRevocation() {
        return Objects.requireNonNull(
                                approveOp,
                                "`body` method should be called before `isNftApprovalRevocation`")
                        .spender()
                        .getAccountNum()
                == 0;
    }

    private Log getLogForFungibleAdjustAllowance(final Address logger) {
        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                .forIndexedArgument(ledgers.canonicalAddress(senderAddress))
                .forIndexedArgument(
                        ledgers.canonicalAddress(asTypedEvmAddress(approveOp.spender())))
                .forDataItem(approveOp.amount())
                .build();
    }

    private Log getLogForNftAdjustAllowance(final Address logger) {
        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                .forIndexedArgument(ledgers.canonicalAddress(senderAddress))
                .forIndexedArgument(
                        ledgers.canonicalAddress(asTypedEvmAddress(approveOp.spender())))
                .forIndexedArgument(approveOp.serialNumber())
                .build();
    }
}
