package com.hedera.services.txns;

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

import com.hedera.services.txns.crypto.CryptoLogicModule;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import com.hedera.services.txns.customfees.FcmCustomFeeSchedules;
import com.hedera.services.txns.file.FileLogicModule;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.hedera.services.txns.span.SpanMapManager;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.txns.validation.OptionValidator;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Module(includes = {
		FileLogicModule.class,
		CryptoLogicModule.class,
})
public abstract class TransactionsModule {
	@Binds
	@Singleton
	public abstract OptionValidator bindOptionValidator(ContextOptionValidator contextOptionValidator);

	@Binds
	@Singleton
	public abstract CustomFeeSchedules bindCustomFeeSchedules(FcmCustomFeeSchedules fcmCustomFeeSchedules);

	@Provides
	@Singleton
	public static ExpandHandleSpan provideExpandHandleSpan(SpanMapManager spanMapManager) {
		return new ExpandHandleSpan(10, TimeUnit.SECONDS, spanMapManager);
	}
}
