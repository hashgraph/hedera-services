package com.hedera.services.txns.contract;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.contract.helpers.UpdateCustomizerFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.swirlds.fcmap.FCMap;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;

import java.util.List;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;

@Module
public class ContractLogicModule {
	@Provides
	@IntoMap
	@FunctionKey(ContractUpdate)
	public static List<TransitionLogic> provideContractUpdateEstimator(
			HederaLedger ledger,
			OptionValidator validator,
			TransactionContext txntCtx,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts
	) {
		final var contractUpdateTransitionLogic = new ContractUpdateTransitionLogic(
			ledger, validator, txntCtx, new UpdateCustomizerFactory(), accounts);
		return List.of(contractUpdateTransitionLogic);
	}
}
