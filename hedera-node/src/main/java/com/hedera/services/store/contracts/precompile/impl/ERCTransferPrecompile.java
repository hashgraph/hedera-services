package com.hedera.services.store.contracts.precompile.impl;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.PrecompileInfoProvider;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;

import java.math.BigInteger;
import java.util.function.UnaryOperator;

import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;

public class ERCTransferPrecompile extends TransferPrecompile {
	private final TokenID tokenID;
	private final AccountID callerAccountID;
	private final boolean isFungible;
	private final EncodingFacade encoder;

	public ERCTransferPrecompile(
			final TokenID tokenID,
			final Address callerAccount,
			final boolean isFungible,
			final WorldLedgers ledgers,
			final DecodingFacade decoder,
			final EncodingFacade encoder,
			final EvmSigsVerifier sigsVerifier,
			final SideEffectsTracker sideEffects,
			final SyntheticTxnFactory syntheticTxnFactory,
			final InfrastructureFactory infrastructureFactory,
			final PrecompilePricingUtils pricingUtils,
			final int functionId,
			final ImpliedTransfersMarshal impliedTransfersMarshal) {
		super(ledgers, decoder, sigsVerifier, sideEffects, syntheticTxnFactory, infrastructureFactory,
				pricingUtils,functionId, callerAccount, impliedTransfersMarshal);
		this.callerAccountID = EntityIdUtils.accountIdFromEvmAddress(callerAccount);
		this.tokenID = tokenID;
		this.isFungible = isFungible;
		this.encoder = encoder;
	}

	@Override
	public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		initializeHederaTokenStore();

		transferOp = switch (input.getInt(0)) {
			case AbiConstants.ABI_ID_ERC_TRANSFER -> decoder.decodeERCTransfer(input, tokenID,
					callerAccountID,
					aliasResolver);
			case AbiConstants.ABI_ID_ERC_TRANSFER_FROM -> {
				final var operatorId = EntityId.fromGrpcAccountId(callerAccountID);
				yield decoder.decodeERCTransferFrom(
						input, tokenID, isFungible, aliasResolver, ledgers, operatorId);
			}
			default -> null;
		};
		transactionBody = syntheticTxnFactory.createCryptoTransfer(transferOp);
		extrapolateDetailsFromSyntheticTxn();
		return transactionBody;
	}

	@Override
	public void run(final PrecompileInfoProvider provider) {
		if (!isFungible) {
			final var nftExchange = transferOp.get(0).nftExchanges().get(0);
			final var nftId = NftId.fromGrpc(nftExchange.getTokenType(), nftExchange.getSerialNo());
			validateTrueOrRevert(ledgers.nfts().contains(nftId), INVALID_TOKEN_NFT_SERIAL_NUMBER);
		}
		try {
			super.run(provider);
		} catch (InvalidTransactionException e) {
			throw new InvalidTransactionException(e.getResponseCode(), true);
		}

		if (isFungible) {
			provider.addLog(getLogForFungibleTransfer(asTypedEvmAddress(tokenID)));
		} else {
			provider.addLog(getLogForNftExchange(asTypedEvmAddress(tokenID)));
		}
	}

	private Log getLogForFungibleTransfer(final Address logger) {
		final var fungibleTransfers = transferOp.get(0).fungibleTransfers();
		Address sender = null;
		Address receiver = null;
		BigInteger amount = BigInteger.ZERO;
		for (final var fungibleTransfer : fungibleTransfers) {
			if (fungibleTransfer.sender() != null) {
				sender = super.ledgers.canonicalAddress(asTypedEvmAddress(fungibleTransfer.sender()));
			}
			if (fungibleTransfer.receiver() != null) {
				receiver = super.ledgers.canonicalAddress(asTypedEvmAddress(fungibleTransfer.receiver()));
				amount = BigInteger.valueOf(fungibleTransfer.amount());
			}
		}

		return EncodingFacade.LogBuilder.logBuilder().forLogger(logger)
				.forEventSignature(AbiConstants.TRANSFER_EVENT)
				.forIndexedArgument(sender)
				.forIndexedArgument(receiver)
				.forDataItem(amount).build();
	}

	private Log getLogForNftExchange(final Address logger) {
		final var nftExchanges = transferOp.get(0).nftExchanges();
		final var nftExchange = nftExchanges.get(0).asGrpc();
		final var sender = super.ledgers.canonicalAddress(asTypedEvmAddress(nftExchange.getSenderAccountID()));
		final var receiver = super.ledgers.canonicalAddress(asTypedEvmAddress(nftExchange.getReceiverAccountID()));
		final var serialNumber = nftExchange.getSerialNumber();

		return EncodingFacade.LogBuilder.logBuilder().forLogger(logger)
				.forEventSignature(AbiConstants.TRANSFER_EVENT)
				.forIndexedArgument(sender)
				.forIndexedArgument(receiver)
				.forIndexedArgument(serialNumber)
				.build();
	}

	@Override
	public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
		if (isFungible) {
			return encoder.encodeEcFungibleTransfer(true);
		} else {
			return Bytes.EMPTY;
		}
	}
}
