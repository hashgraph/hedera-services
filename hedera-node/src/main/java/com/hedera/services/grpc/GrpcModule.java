package com.hedera.services.grpc;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.grpc.marshalling.AdjustmentUtils;
import com.hedera.services.grpc.marshalling.BalanceChangeManager;
import com.hedera.services.grpc.marshalling.CustomSchedulesManager;
import com.hedera.services.grpc.marshalling.FeeAssessor;
import com.hedera.services.grpc.marshalling.FixedFeeAssessor;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.grpc.marshalling.RoyaltyFeeAssessor;
import com.hedera.services.ledger.PureTransferSemanticChecks;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.function.Consumer;

@Module
public abstract class GrpcModule {
	@Provides
	@Singleton
	public static Consumer<Thread> provideHookAdder() {
		return Runtime.getRuntime()::addShutdownHook;
	}

	@Provides
	@Singleton
	public static RoyaltyFeeAssessor provideRoyaltyFeeAssessor(FixedFeeAssessor fixedFeeAssessor) {
		return new RoyaltyFeeAssessor(fixedFeeAssessor, AdjustmentUtils::adjustedChange);
	}

	@Provides
	@Singleton
	public static ImpliedTransfersMarshal provideImpliedTransfersMarshal(
			FeeAssessor feeAssessor,
			CustomFeeSchedules customFeeSchedules,
			GlobalDynamicProperties dynamicProperties,
			PureTransferSemanticChecks transferSemanticChecks
	) {
		return new ImpliedTransfersMarshal(
				feeAssessor,
				customFeeSchedules,
				dynamicProperties,
				transferSemanticChecks,
				BalanceChangeManager::new,
				CustomSchedulesManager::new);
	}
}
