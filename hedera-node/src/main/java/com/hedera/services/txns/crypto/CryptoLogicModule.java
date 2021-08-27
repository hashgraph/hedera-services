package com.hedera.services.txns.crypto;

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
	public static List<TransitionLogic> provideCryptoCreateLogic(
			CryptoCreateTransitionLogic cryptoCreateTransitionLogic
	) {
		return List.of(cryptoCreateTransitionLogic);
	}

	@Provides
	@IntoMap
	@FunctionKey(CryptoUpdate)
	public static List<TransitionLogic> provideCryptoUpdateLogic(
			CryptoUpdateTransitionLogic cryptoUpdateTransitionLogic
	) {
		return List.of(cryptoUpdateTransitionLogic);
	}

	@Provides
	@IntoMap
	@FunctionKey(CryptoDelete)
	public static List<TransitionLogic> provideCryptoDeleteLogic(
			CryptoDeleteTransitionLogic cryptoDeleteTransitionLogic
	) {
		return List.of(cryptoDeleteTransitionLogic);
	}

	@Provides
	@IntoMap
	@FunctionKey(CryptoTransfer)
	public static List<TransitionLogic> provideCryptoTransferLogic(
			CryptoTransferTransitionLogic cryptoTransferTransitionLogic
	) {
		return List.of(cryptoTransferTransitionLogic);
	}
}
