package com.hedera.services.store.contracts.precompile.impl;

/*-
 * ‌
 * Hedera Services Node
 *
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
 * ‍
 */

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Objects;
import java.util.function.UnaryOperator;

import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.APPROVE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.DELETE_NFT_APPROVE;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;

public class ApprovePrecompile extends AbstractWritePrecompile {
	private final TokenID tokenId;
	private final boolean isFungible;
	private final EncodingFacade encoder;
	private final Address senderAddress;
	private final StateView currentView;

	private TransactionBody.Builder transactionBody;
	private ApproveWrapper approveOp;
	@Nullable
	private EntityId operatorId;
	@Nullable
	private EntityId ownerId;


	public ApprovePrecompile(
			final TokenID tokenId,
			final boolean isFungible,
			final WorldLedgers ledgers,
			final DecodingFacade decoder,
			final EncodingFacade encoder,
			final StateView currentView,
			final SideEffectsTracker sideEffects,
			final SyntheticTxnFactory syntheticTxnFactory,
			final InfrastructureFactory infrastructureFactory,
			final PrecompilePricingUtils pricingUtils,
			final Address senderAddress) {
		super(ledgers, decoder, sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
		this.tokenId = tokenId;
		this.isFungible = isFungible;
		this.encoder = encoder;
		this.senderAddress = senderAddress;
		this.currentView = currentView;
	}

	@Override
	public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final var nestedInput = input.slice(24);
		operatorId = EntityId.fromAddress(senderAddress);
		approveOp = decoder.decodeTokenApprove(nestedInput, tokenId, isFungible, aliasResolver);
		if (isFungible) {
			transactionBody = syntheticTxnFactory.createFungibleApproval(approveOp);
			return transactionBody;
		} else {
			final var nftId = NftId.fromGrpc(tokenId, approveOp.serialNumber().longValue());
			ownerId = ledgers.ownerIfPresent(nftId);
			// Per the ERC-721 spec, "The zero address indicates there is no approved address"; so
			// translate this approveAllowance into a deleteAllowance
			if (isNftApprovalRevocation()) {
				final var nominalOwnerId = ownerId != null ? ownerId : MISSING_ENTITY_ID;
				transactionBody = syntheticTxnFactory.createDeleteAllowance(approveOp, nominalOwnerId);
			} else {
				transactionBody = syntheticTxnFactory.createNonfungibleApproval(approveOp, ownerId, operatorId);
			}
			return transactionBody;
		}
	}

	@Override
	public void run(final MessageFrame frame) {
		Objects.requireNonNull(approveOp);

		validateTrueOrRevert(isFungible || ownerId != null, INVALID_TOKEN_NFT_SERIAL_NUMBER);
		final var grpcOperatorId = Objects.requireNonNull(operatorId).toGrpcAccountId();
		//  Per the ERC-721 spec, "Throws unless `msg.sender` is the current NFT owner, or
		//  an authorized operator of the current owner"
		if (!isFungible) {
			final var isApproved = operatorId.equals(ownerId) ||
					ledgers.hasApprovedForAll(ownerId.toGrpcAccountId(), grpcOperatorId, tokenId);
			validateTrueOrRevert(isApproved, SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
		}

		// --- Build the necessary infrastructure to execute the transaction ---
		final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
		final var tokenStore = infrastructureFactory.newTokenStore(
				accountStore, sideEffects, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());
		final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(grpcOperatorId));
		final var approveAllowanceChecks = infrastructureFactory.newApproveAllowanceChecks();
		final var deleteAllowanceChecks = infrastructureFactory.newDeleteAllowanceChecks();
		// --- Execute the transaction and capture its results ---
		if (isNftApprovalRevocation()) {
			final var deleteAllowanceLogic =
					infrastructureFactory.newDeleteAllowanceLogic(accountStore, tokenStore);
			final var revocationOp = transactionBody.getCryptoDeleteAllowance();
			final var revocationWrapper = revocationOp.getNftAllowancesList();
			final var status = deleteAllowanceChecks.deleteAllowancesValidation(
					revocationWrapper, payerAccount, currentView);
			validateTrueOrRevert(status == OK, status);
			deleteAllowanceLogic.deleteAllowance(revocationWrapper, grpcOperatorId);
		} else {
			final var status = approveAllowanceChecks.allowancesValidation(
					transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
					transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
					transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
					payerAccount,
					currentView);
			validateTrueOrRevert(status == OK, status);
			final var approveAllowanceLogic = infrastructureFactory.newApproveAllowanceLogic(
					accountStore, tokenStore);
			try {
				approveAllowanceLogic.approveAllowance(
						transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
						transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
						transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
						grpcOperatorId);
			} catch (InvalidTransactionException e) {
				throw InvalidTransactionException.fromReverting(e.getResponseCode());
			}
		}
		final var precompileAddress = Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS);

		if (isFungible) {
			frame.addLog(getLogForFungibleAdjustAllowance(precompileAddress));
		} else {
			frame.addLog(getLogForNftAdjustAllowance(precompileAddress));
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
		return encoder.encodeApprove(true);
	}

	@Override
	public long getGasRequirement(long blockTimestamp) {
		return pricingUtils.computeGasRequirement(blockTimestamp,this, transactionBody);
	}

	private boolean isNftApprovalRevocation() {
		return Objects.requireNonNull(approveOp).spender().getAccountNum() == 0;
	}

	private Log getLogForFungibleAdjustAllowance(final Address logger) {
		return EncodingFacade.LogBuilder.logBuilder()
				.forLogger(logger)
				.forEventSignature(AbiConstants.APPROVAL_EVENT)
				.forIndexedArgument(senderAddress)
				.forIndexedArgument(asTypedEvmAddress(approveOp.spender()))
				.forDataItem(BigInteger.valueOf(approveOp.amount().longValue())).build();
	}

	private Log getLogForNftAdjustAllowance(final Address logger) {
		return EncodingFacade.LogBuilder.logBuilder()
				.forLogger(logger)
				.forEventSignature(AbiConstants.APPROVAL_EVENT)
				.forIndexedArgument(senderAddress)
				.forIndexedArgument(asTypedEvmAddress(approveOp.spender()))
				.forIndexedArgument(BigInteger.valueOf(approveOp.serialNumber().longValue())).build();
	}
}
