package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Objects;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.ASSOCIATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/* --- Constructor functional interfaces for mocking --- */
public abstract class AbstractAssociatePrecompile implements Precompile {
	private final WorldLedgers ledgers;
	private final ContractAliases aliases;
	private final EvmSigsVerifier sigsVerifier;
	private final SideEffectsTracker sideEffects;
	private final InfrastructureFactory infrastructureFactory;
	private final PrecompilePricingUtils pricingUtils;


	protected TransactionBody.Builder transactionBody;
	protected Association associateOp;
	protected final DecodingFacade decoder;
	protected final SyntheticTxnFactory syntheticTxnFactory;

	protected AbstractAssociatePrecompile(
			final WorldLedgers ledgers,
			final DecodingFacade decoder,
			final ContractAliases aliases,
			final EvmSigsVerifier sigsVerifier,
			final SideEffectsTracker sideEffects,
			final SyntheticTxnFactory syntheticTxnFactory,
			final InfrastructureFactory infrastructureFactory,
			final PrecompilePricingUtils pricingUtils
	) {
		this.decoder = decoder;
		this.ledgers = ledgers;
		this.aliases = aliases;
		this.sigsVerifier = sigsVerifier;
		this.sideEffects = sideEffects;
		this.pricingUtils = pricingUtils;
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.infrastructureFactory = infrastructureFactory;
	}

	@Override
	public void run(final MessageFrame frame) {
		// --- Check required signatures ---
		final var accountId = Id.fromGrpcAccount(Objects.requireNonNull(associateOp).accountId());
		final var hasRequiredSigs = KeyActivationUtils.validateKey(
				frame, accountId.asEvmAddress(), sigsVerifier::hasActiveKey, ledgers, aliases);
		validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

		// --- Build the necessary infrastructure to execute the transaction ---
		final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
		final var tokenStore = infrastructureFactory.newTokenStore(
				accountStore, sideEffects, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());

		// --- Execute the transaction and capture its results ---
		final var associateLogic = infrastructureFactory.newAssociateLogic(accountStore, tokenStore);
		final var validity = associateLogic.validateSyntax(transactionBody.build());
		validateTrue(validity == OK, validity);
		associateLogic.associate(accountId, associateOp.tokenIds());
	}

	@Override
	public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
		return pricingUtils.getMinimumPriceInTinybars(ASSOCIATE, consensusTime);
	}
}
