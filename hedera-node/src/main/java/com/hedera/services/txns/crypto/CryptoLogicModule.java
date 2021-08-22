package com.hedera.services.txns.crypto;

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.txns.TransitionLogic;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;

import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;

@Module
public abstract class CryptoLogicModule {
	@Provides
	@IntoMap
	@FunctionKey(CryptoCreate)
	public static List<TransitionLogic> provideCryptoCreateEstimator(
			CryptoCreateTransitionLogic cryptoCreateTransitionLogic
	) {
		return List.of(cryptoCreateTransitionLogic);
	}

	@Provides
	@IntoMap
	@FunctionKey(CryptoUpdate)
	public static List<TransitionLogic> provideCryptoUpdateEstimator(
			CryptoUpdateTransitionLogic cryptoUpdateTransitionLogic
	) {
		return List.of(cryptoUpdateTransitionLogic);
	}

	@Provides
	@IntoMap
	@FunctionKey(CryptoDelete)
	public static List<TransitionLogic> provideCryptoDeleteEstimator(
			CryptoDeleteTransitionLogic cryptoDeleteTransitionLogic
	) {
		return List.of(cryptoDeleteTransitionLogic);
	}

	@Provides
	@IntoMap
	@FunctionKey(CryptoTransfer)
	public static List<TransitionLogic> provideCryptoTransferEstimator(
			CryptoTransferTransitionLogic cryptoTransferTransitionLogic
	) {
		return List.of(cryptoTransferTransitionLogic);
	}
}
