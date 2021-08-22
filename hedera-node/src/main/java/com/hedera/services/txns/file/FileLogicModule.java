package com.hedera.services.txns.file;

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.txns.TransitionLogic;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;

import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;

@Module
public abstract class FileLogicModule {
	@Provides
	@IntoMap
	@FunctionKey(FileUpdate)
	public static List<TransitionLogic> provideFileUpdateEstimator(
			FileUpdateTransitionLogic fileUpdateTransitionLogic
	) {
		return List.of(fileUpdateTransitionLogic);
	}

	@Provides
	@IntoMap
	@FunctionKey(FileCreate)
	public static List<TransitionLogic> provideFileCreateEstimator(
			FileCreateTransitionLogic fileCreateTransitionLogic
	) {
		return List.of(fileCreateTransitionLogic);
	}

	@Provides
	@IntoMap
	@FunctionKey(FileDelete)
	public static List<TransitionLogic> provideFileDeleteEstimator(
			FileDeleteTransitionLogic fileDeleteTransitionLogic
	) {
		return List.of(fileDeleteTransitionLogic);
	}

	@Provides
	@IntoMap
	@FunctionKey(FileAppend)
	public static List<TransitionLogic> provideFileAppendEstimator(
			FileAppendTransitionLogic fileAppendTransitionLogic
	) {
		return List.of(fileAppendTransitionLogic);
	}
}
