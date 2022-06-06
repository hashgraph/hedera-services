package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;

import java.util.function.UnaryOperator;

public class MultiAssociatePrecompile extends AbstractAssociatePrecompile {
	public MultiAssociatePrecompile(
			final WorldLedgers ledgers,
			final DecodingFacade decoder,
			final ContractAliases aliases,
			final EvmSigsVerifier sigsVerifier,
			final SideEffectsTracker sideEffects,
			final SyntheticTxnFactory syntheticTxnFactory,
			final InfrastructureFactory infrastructureFactory,
			final PrecompilePricingUtils pricingUtils
	) {
		super(
				ledgers, decoder, aliases, sigsVerifier,
				sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
	}

	@Override
	public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		associateOp = decoder.decodeMultipleAssociations(input, aliasResolver);
		transactionBody = syntheticTxnFactory.createAssociate(associateOp);
		return transactionBody;
	}
}
