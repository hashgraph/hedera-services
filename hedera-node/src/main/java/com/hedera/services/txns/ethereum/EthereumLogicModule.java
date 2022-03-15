package com.hedera.services.txns.ethereum;

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.txns.TransitionLogic;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;

import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;

@Module
public class EthereumLogicModule {

	@Provides
	@IntoMap
	@FunctionKey(EthereumTransaction)
	public static List<TransitionLogic> provideContractCallLogic(
			final EthereumTransitionLogic ethereumTransitionLogic
	) {
		return List.of(ethereumTransitionLogic);
	}

}
