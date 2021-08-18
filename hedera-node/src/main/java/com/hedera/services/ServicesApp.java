package com.hedera.services;

import com.hedera.services.state.DistinguishedStates;
import com.hedera.services.state.annotations.FirstWorkingState;
import dagger.BindsInstance;
import dagger.Component;

import javax.inject.Singleton;

@Component
@Singleton
public interface ServicesApp {
	DistinguishedStates distinguishedStates();

	@Component.Builder
	interface Builder {
		@BindsInstance
		Builder initialState(@FirstWorkingState ServicesState state);
		ServicesApp build();
	}
}
