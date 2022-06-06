package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Objects;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.DISSOCIATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

public abstract class AbstractDissociatePrecompile implements Precompile {
	private final WorldLedgers ledgers;
	private final ContractAliases aliases;
	private final EvmSigsVerifier sigsVerifier;
	private final SideEffectsTracker sideEffects;
	private final InfrastructureFactory infrastructureFactory;
	private final PrecompilePricingUtils pricingUtils;

	protected Dissociation dissociateOp;
	protected final DecodingFacade decoder;
	protected final SyntheticTxnFactory syntheticTxnFactory;

	public AbstractDissociatePrecompile(
			final WorldLedgers ledgers,
			final DecodingFacade decoder,
			final ContractAliases aliases,
			final EvmSigsVerifier sigsVerifier,
			final SideEffectsTracker sideEffects,
			final SyntheticTxnFactory syntheticTxnFactory,
			final InfrastructureFactory infrastructureFactory,
			final PrecompilePricingUtils pricingUtils

	) {
		this.ledgers = ledgers;
		this.aliases = aliases;
		this.sigsVerifier = sigsVerifier;
		this.sideEffects = sideEffects;
		this.infrastructureFactory = infrastructureFactory;
		this.pricingUtils = pricingUtils;
		this.decoder = decoder;
		this.syntheticTxnFactory = syntheticTxnFactory;

	}

	@Override
	public void run(
			final MessageFrame frame
	) {
		Objects.requireNonNull(dissociateOp);

		/* --- Check required signatures --- */
		final var accountId = Id.fromGrpcAccount(dissociateOp.accountId());
		final var hasRequiredSigs = KeyActivationUtils.validateKey(
				frame, accountId.asEvmAddress(), sigsVerifier::hasActiveKey, ledgers, aliases);
		validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

		/* --- Build the necessary infrastructure to execute the transaction --- */
		final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
		final var tokenStore = infrastructureFactory.newTokenStore(
				accountStore, sideEffects, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());

		/* --- Execute the transaction and capture its results --- */
		final var dissociateLogic = infrastructureFactory.newDissociateLogic(accountStore, tokenStore);
		//TODO add txn body for this check
//		final var validity = dissociateLogic.validateSyntax(transactionBody.build());
//		validateTrue(validity == OK, validity);
		dissociateLogic.dissociate(accountId, dissociateOp.tokenIds());
	}

	@Override
	public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
		return pricingUtils.getMinimumPriceInTinybars(DISSOCIATE, consensusTime);
	}
}
