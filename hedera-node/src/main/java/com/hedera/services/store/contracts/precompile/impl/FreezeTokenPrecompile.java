package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;

import java.util.Objects;
import java.util.function.UnaryOperator;

import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.FREEZE;

public class FreezeTokenPrecompile extends AbstractFreezeUnfreezePrecompile{

	public FreezeTokenPrecompile(
			WorldLedgers ledgers,
			DecodingFacade decoder,
			final ContractAliases aliases,
			final EvmSigsVerifier sigsVerifier,
			SideEffectsTracker sideEffects,
			SyntheticTxnFactory syntheticTxnFactory,
			InfrastructureFactory infrastructureFactory,
			PrecompilePricingUtils pricingUtils,
			boolean isFreeze) {
		super(
				ledgers,
				decoder,
				aliases,
				sigsVerifier,
				sideEffects,
				syntheticTxnFactory,
				infrastructureFactory,
				pricingUtils,
				isFreeze);
	}
	@Override
	public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
		freezeUnfreezeOp = decoder.decodeFreeze(input, aliasResolver);
		transactionBody = syntheticTxnFactory.createFreeze(freezeUnfreezeOp);
		return transactionBody;
	}

	@Override
	public long getMinimumFeeInTinybars(Timestamp consensusTime) {
		Objects.requireNonNull(freezeUnfreezeOp);
		return pricingUtils.getMinimumPriceInTinybars(FREEZE, consensusTime);
	}
}
