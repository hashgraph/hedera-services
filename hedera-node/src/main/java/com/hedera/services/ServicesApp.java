package com.hedera.services;

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

import com.hedera.services.context.ContextModule;
import com.hedera.services.context.init.FullInitializationFlow;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.context.properties.PropertiesModule;
import com.hedera.services.contracts.ContractsModule;
import com.hedera.services.fees.FeesModule;
import com.hedera.services.files.FilesModule;
import com.hedera.services.grpc.GrpcModule;
import com.hedera.services.keys.KeysModule;
import com.hedera.services.ledger.LedgerModule;
import com.hedera.services.records.RecordsModule;
import com.hedera.services.sigs.SigsModule;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.state.StateModule;
import com.hedera.services.stats.StatsModule;
import com.hedera.services.store.StoresModule;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.throttling.ThrottlingModule;
import com.hedera.services.txns.TransactionsModule;
import com.hedera.services.txns.submission.SubmissionModule;
import com.swirlds.common.Platform;
import dagger.BindsInstance;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
		FeesModule.class,
		KeysModule.class,
		SigsModule.class,
		GrpcModule.class,
		StatsModule.class,
		StateModule.class,
		FilesModule.class,
		LedgerModule.class,
		StoresModule.class,
		ContextModule.class,
		RecordsModule.class,
		ContractsModule.class,
		PropertiesModule.class,
		ThrottlingModule.class,
		SubmissionModule.class,
		TransactionsModule.class,
})
public interface ServicesApp {
	DualStateAccessor dualStateAccessor();
	RecordStreamManager recordStreamManager();
	NodeLocalProperties nodeLocalProperties();
	GlobalDynamicProperties globalDynamicProperties();
	FullInitializationFlow initializationFlow();

	@Component.Builder
	interface Builder {
		@BindsInstance
		Builder platform(Platform platform);
		@BindsInstance
		Builder selfId(long selfId);
		@BindsInstance
		Builder initialState(ServicesState initialState);

		ServicesApp build();
	}
}
