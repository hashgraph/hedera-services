package com.hedera.services.state;

import com.hedera.services.ServicesState;
import com.hedera.services.state.annotations.FirstWorkingState;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DistinguishedStates {
	private ServicesState workingState;

	@Inject
	public DistinguishedStates(@FirstWorkingState ServicesState workingState) {
		System.out.println(System.identityHashCode(this)
				+ " distinguished states gets state "
				+ System.identityHashCode(workingState));
		this.workingState = workingState;
	}
}
