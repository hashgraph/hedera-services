package com.hedera.services.context.init;

import com.hedera.services.ServicesState;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FullInitializationFlow {
	private final StateInitializationFlow stateFlow;
	private final StoreInitializationFlow storeFlow;
	private final EntitiesInitializationFlow entitiesFlow;

	@Inject
	public FullInitializationFlow(
			StateInitializationFlow stateFlow,
			StoreInitializationFlow storeFlow,
			EntitiesInitializationFlow entitiesFlow
	) {
		this.stateFlow = stateFlow;
		this.storeFlow = storeFlow;
		this.entitiesFlow = entitiesFlow;
	}

	public void runWith(ServicesState activeState) {
		stateFlow.runWith(activeState);
		storeFlow.run();
		entitiesFlow.run();
	}
}
