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
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.BURN_FUNGIBLE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.BURN_NFT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_BURN_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class BurnPrecompile extends AbstractWritePrecompile {
	private static final List<Long> NO_SERIAL_NOS = Collections.emptyList();
	private final EncodingFacade encoder;
	private final ContractAliases aliases;
	private final EvmSigsVerifier sigsVerifier;
	private BurnWrapper burnOp;

	public BurnPrecompile(
			final WorldLedgers ledgers,
			final DecodingFacade decoder,
			final EncodingFacade encoder,
			final ContractAliases aliases,
			final EvmSigsVerifier sigsVerifier,
			final SideEffectsTracker sideEffects,
			final SyntheticTxnFactory syntheticTxnFactory,
			final InfrastructureFactory infrastructureFactory,
			final PrecompilePricingUtils pricingUtils
	) {
		super(ledgers, decoder, sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
		this.encoder = encoder;
		this.aliases = aliases;
		this.sigsVerifier = sigsVerifier;
	}

	@Override
	public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		burnOp = decoder.decodeBurn(input);
		transactionBody = syntheticTxnFactory.createBurn(burnOp);
		return transactionBody;
	}

	@Override
	public void run(final MessageFrame frame) {
		Objects.requireNonNull(burnOp);

		/* --- Check required signatures --- */
		final var tokenId = Id.fromGrpcToken(burnOp.tokenType());
		final var hasRequiredSigs = KeyActivationUtils.validateKey(
				frame, tokenId.asEvmAddress(), sigsVerifier::hasActiveSupplyKey, ledgers, aliases);
		validateTrue(hasRequiredSigs, INVALID_FULL_PREFIX_SIGNATURE_FOR_BURN_PRECOMPILE);

		/* --- Build the necessary infrastructure to execute the transaction --- */
		final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
		final var tokenStore = infrastructureFactory.newTokenStore(
				accountStore, sideEffects, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());
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
		Objects.requireNonNull(burnOp);
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
}
