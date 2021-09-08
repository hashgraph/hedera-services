package com.hedera.services.statecreation;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.txns.submission.BasicSubmissionFlow;
import com.hedera.services.txns.submission.PlatformSubmissionManager;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public abstract class StateCreateModule {
	private StateCreateModule() {}
	@Provides
	@Singleton
	public static StateCreationManager provideStateCreateManager(final PlatformSubmissionManager submissionManager,
			final BasicSubmissionFlow submissionFlow,
			final NodeLocalProperties nodeLocalProperties,
			final NetworkCtxManager networkCtxManager) {
		return new StateCreationManager(submissionManager, submissionFlow,
				nodeLocalProperties, networkCtxManager);
	}
}
