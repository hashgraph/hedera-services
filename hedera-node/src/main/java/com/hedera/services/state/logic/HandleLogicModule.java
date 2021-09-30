package com.hedera.services.state.logic;

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

import com.hedera.services.sigs.SigsModule;
import com.hedera.services.state.annotations.RunRecordStreaming;
import com.hedera.services.state.annotations.RunTopLevelTransition;
import com.hedera.services.state.annotations.RunTriggeredTransition;
import com.hedera.services.txns.ProcessLogic;
import dagger.Binds;
import dagger.Module;

import javax.inject.Singleton;

@Module(includes = SigsModule.class)
public abstract class HandleLogicModule {
	@Binds
	@Singleton
	@RunRecordStreaming
	public abstract Runnable provideRecordStreaming(RecordStreaming recordStreaming);

	@Binds
	@Singleton
	@RunTopLevelTransition
	public abstract Runnable provideTopLevelTransition(TopLevelTransition topLevelTransition);

	@Binds
	@Singleton
	@RunTriggeredTransition
	public abstract Runnable provideTriggeredTransition(TriggeredTransition triggeredTransition);

	@Binds
	@Singleton
	public abstract ProcessLogic provideProcessLogic(StandardProcessLogic standardProcessLogic);
}
