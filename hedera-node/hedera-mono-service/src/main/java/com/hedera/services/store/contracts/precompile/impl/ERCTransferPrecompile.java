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
import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.NO_FUNGIBLE_TRANSFERS;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.NO_NFT_EXCHANGES;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.addApprovedAdjustment;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.addSignedAdjustment;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.CryptoTransferWrapper;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenTransferWrapper;
import com.hedera.services.store.contracts.precompile.codec.TransferWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

public class ERCTransferPrecompile extends TransferPrecompile {
    private static final Function ERC_TRANSFER_FUNCTION =
            new Function("transfer(address,uint256)", BOOL);
    private static final Bytes ERC_TRANSFER_SELECTOR = Bytes.wrap(ERC_TRANSFER_FUNCTION.selector());
    private static final ABIType<Tuple> ERC_TRANSFER_DECODER =
            TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);
    private static final Function ERC_TRANSFER_FROM_FUNCTION =
            new Function("transferFrom(address,address,uint256)");
    private static final Bytes ERC_TRANSFER_FROM_SELECTOR =
            Bytes.wrap(ERC_TRANSFER_FROM_FUNCTION.selector());
    private static final ABIType<Tuple> ERC_TRANSFER_FROM_DECODER =
            TypeFactory.create(ADDRESS_ADDRESS_UINT256_RAW_TYPE);
    private static final Function HAPI_TRANSFER_FROM_FUNCTION =
            new Function("transferFrom(address,address,address,uint256)");
    private static final Bytes HAPI_TRANSFER_FROM_SELECTOR =
            Bytes.wrap(HAPI_TRANSFER_FROM_FUNCTION.selector());
    private static final Function HAPI_TRANSFER_FROM_NFT_FUNCTION =
            new Function("transferFromNFT(address,address,address,uint256)");
    private static final Bytes HAPI_TRANSFER_FROM_NFT_SELECTOR =
            Bytes.wrap(HAPI_TRANSFER_FROM_NFT_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_TRANSFER_FROM_DECODER =
            TypeFactory.create("(bytes32,bytes32,bytes32,uint256)");

    private final TokenID tokenID;
    private final AccountID callerAccountID;
    private final boolean isFungible;
    private final EncodingFacade encoder;

    public ERCTransferPrecompile(
            final TokenID tokenID,
            final Address callerAccount,
            final boolean isFungible,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final HederaStackedWorldStateUpdater updater,
            final EvmSigsVerifier sigsVerifier,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final int functionId,
            final ImpliedTransfersMarshal impliedTransfersMarshal) {
        super(
                ledgers,
                updater,
                sigsVerifier,
                sideEffects,
                syntheticTxnFactory,
                infrastructureFactory,
                pricingUtils,
                functionId,
                callerAccount,
                impliedTransfersMarshal);
        this.callerAccountID = EntityIdUtils.accountIdFromEvmAddress(callerAccount);
        this.tokenID = tokenID;
        this.isFungible = isFungible;
        this.encoder = encoder;
    }

    public ERCTransferPrecompile(
            final Address callerAccount,
            final boolean isFungible,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final HederaStackedWorldStateUpdater updater,
            final EvmSigsVerifier sigsVerifier,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final int functionId,
            final ImpliedTransfersMarshal impliedTransfersMarshal) {
        this(
                null,
                callerAccount,
                isFungible,
                ledgers,
                encoder,
                updater,
                sigsVerifier,
                sideEffects,
                syntheticTxnFactory,
                infrastructureFactory,
                pricingUtils,
                functionId,
                impliedTransfersMarshal);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        initializeHederaTokenStore();

        final var nestedInput = tokenID == null ? input : input.slice(24);
        transferOp =
                switch (nestedInput.getInt(0)) {
                    case AbiConstants.ABI_ID_ERC_TRANSFER -> decodeERCTransfer(
                            nestedInput, tokenID, callerAccountID, aliasResolver);
                    case AbiConstants.ABI_ID_ERC_TRANSFER_FROM,
                            AbiConstants.ABI_ID_TRANSFER_FROM,
                            AbiConstants.ABI_ID_TRANSFER_FROM_NFT -> {
                        final var operatorId = EntityId.fromGrpcAccountId(callerAccountID);
                        yield decodeERCTransferFrom(
                                nestedInput,
                                tokenID,
                                isFungible,
                                aliasResolver,
                                ledgers,
                                operatorId);
                    }
                    default -> null;
                };
        Objects.requireNonNull(transferOp, "Unable to decode function input");

        transactionBody =
                syntheticTxnFactory.createCryptoTransfer(transferOp.tokenTransferWrappers());
        extrapolateDetailsFromSyntheticTxn();
        return transactionBody;
    }

    @Override
    public void run(final MessageFrame frame) {
        Objects.requireNonNull(transferOp, "`body` method should be called before `run`");

        if (!isFungible) {
            final var nftExchange = transferOp.tokenTransferWrappers().get(0).nftExchanges().get(0);
            final var nftId = NftId.fromGrpc(nftExchange.getTokenType(), nftExchange.getSerialNo());
            validateTrueOrRevert(ledgers.nfts().contains(nftId), INVALID_TOKEN_NFT_SERIAL_NUMBER);
        }
        try {
            super.run(frame);
        } catch (InvalidTransactionException e) {
            throw new InvalidTransactionException(e.getResponseCode(), true);
        }

        final var tokenAddress =
                asTypedEvmAddress(tokenID != null ? tokenID : extractTokenIdFromSyntheticOp());
        if (isFungible) {
            frame.addLog(getLogForFungibleTransfer(tokenAddress));
        } else {
            frame.addLog(getLogForNftExchange(tokenAddress));
        }
    }

    public static CryptoTransferWrapper decodeERCTransfer(
            final Bytes input,
            final TokenID token,
            final AccountID caller,
            final UnaryOperator<byte[]> aliasResolver) {
        final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments =
                decodeFunctionCall(input, ERC_TRANSFER_SELECTOR, ERC_TRANSFER_DECODER);

        final var recipient =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(0), aliasResolver);
        final var amount = (BigInteger) decodedArguments.get(1);

        final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
        addSignedAdjustment(fungibleTransfers, token, recipient, amount.longValueExact());
        addSignedAdjustment(fungibleTransfers, token, caller, -amount.longValueExact());

        final var tokenTransferWrappers =
                Collections.singletonList(
                        new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static CryptoTransferWrapper decodeERCTransferFrom(
            final Bytes input,
            final TokenID impliedTokenId,
            final boolean isFungible,
            final UnaryOperator<byte[]> aliasResolver,
            final WorldLedgers ledgers,
            final EntityId operatorId) {

        final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = Collections.emptyList();
        final var offset = impliedTokenId == null ? 1 : 0;
        final Tuple decodedArguments;
        final TokenID token;

        if (offset == 0) {
            decodedArguments =
                    decodeFunctionCall(
                            input, ERC_TRANSFER_FROM_SELECTOR, ERC_TRANSFER_FROM_DECODER);
            token = impliedTokenId;
        } else {
            decodedArguments =
                    decodeFunctionCall(
                            input,
                            isFungible
                                    ? HAPI_TRANSFER_FROM_SELECTOR
                                    : HAPI_TRANSFER_FROM_NFT_SELECTOR,
                            HAPI_TRANSFER_FROM_DECODER);
            token = convertAddressBytesToTokenID(decodedArguments.get(0));
        }

        final var from =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(offset), aliasResolver);
        final var to =
                convertLeftPaddedAddressToAccountId(
                        decodedArguments.get(offset + 1), aliasResolver);

        if (isFungible) {
            final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers =
                    new ArrayList<>();
            final var amount = (BigInteger) decodedArguments.get(offset + 2);

            addSignedAdjustment(fungibleTransfers, token, to, amount.longValueExact());

            if (from.equals(operatorId.toGrpcAccountId())) {
                addSignedAdjustment(fungibleTransfers, token, from, -amount.longValueExact());
            } else {
                addApprovedAdjustment(fungibleTransfers, token, from, -amount.longValueExact());
            }

            final var tokenTransferWrappers =
                    Collections.singletonList(
                            new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));
            return new CryptoTransferWrapper(
                    new TransferWrapper(hbarTransfers), tokenTransferWrappers);
        } else {
            final List<SyntheticTxnFactory.NftExchange> nonFungibleTransfers = new ArrayList<>();
            final var serialNo = ((BigInteger) decodedArguments.get(offset + 2)).longValueExact();
            final var ownerId = ledgers.ownerIfPresent(NftId.fromGrpc(token, serialNo));

            if (operatorId.equals(ownerId)) {
                nonFungibleTransfers.add(
                        new SyntheticTxnFactory.NftExchange(serialNo, token, from, to));
            } else {
                nonFungibleTransfers.add(
                        SyntheticTxnFactory.NftExchange.fromApproval(serialNo, token, from, to));
            }

            final var tokenTransferWrappers =
                    Collections.singletonList(
                            new TokenTransferWrapper(nonFungibleTransfers, NO_FUNGIBLE_TRANSFERS));
            return new CryptoTransferWrapper(
                    new TransferWrapper(hbarTransfers), tokenTransferWrappers);
        }
    }

    private Log getLogForFungibleTransfer(final Address logger) {
        final var fungibleTransfers = transferOp.tokenTransferWrappers().get(0).fungibleTransfers();
        Address sender = null;
        Address receiver = null;
        BigInteger amount = BigInteger.ZERO;
        for (final var fungibleTransfer : fungibleTransfers) {
            if (fungibleTransfer.sender() != null) {
                sender =
                        super.ledgers.canonicalAddress(
                                asTypedEvmAddress(fungibleTransfer.sender()));
            }
            if (fungibleTransfer.receiver() != null) {
                receiver =
                        super.ledgers.canonicalAddress(
                                asTypedEvmAddress(fungibleTransfer.receiver()));
                amount = BigInteger.valueOf(fungibleTransfer.amount());
            }
        }

        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.TRANSFER_EVENT)
                .forIndexedArgument(sender)
                .forIndexedArgument(receiver)
                .forDataItem(amount)
                .build();
    }

    private Log getLogForNftExchange(final Address logger) {
        final var nftExchanges = transferOp.tokenTransferWrappers().get(0).nftExchanges();
        final var nftExchange = nftExchanges.get(0).asGrpc();
        final var sender =
                super.ledgers.canonicalAddress(asTypedEvmAddress(nftExchange.getSenderAccountID()));
        final var receiver =
                super.ledgers.canonicalAddress(
                        asTypedEvmAddress(nftExchange.getReceiverAccountID()));
        final var serialNumber = nftExchange.getSerialNumber();

        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.TRANSFER_EVENT)
                .forIndexedArgument(sender)
                .forIndexedArgument(receiver)
                .forIndexedArgument(serialNumber)
                .build();
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        if (tokenID != null) {
            return isFungible ? encoder.encodeEcFungibleTransfer(true) : Bytes.EMPTY;
        } else {
            return super.getSuccessResultFor(childRecord);
        }
    }

    private TokenID extractTokenIdFromSyntheticOp() {
        if (isFungible) {
            return transferOp
                    .tokenTransferWrappers()
                    .get(0)
                    .fungibleTransfers()
                    .get(0)
                    .getDenomination();
        } else {
            return transferOp.tokenTransferWrappers().get(0).nftExchanges().get(0).getTokenType();
        }
    }
}
