package com.hedera.services.txns.util;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.txns.consensus.SubmitMessageTransitionLogic;
import com.hedera.services.txns.consensus.TopicCreateTransitionLogic;
import com.hedera.services.txns.consensus.TopicDeleteTransitionLogic;
import com.hedera.services.txns.consensus.TopicUpdateTransitionLogic;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;

import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.RandomGenerate;

@Module
public final class UtilLogicModule {
	@Provides
	@IntoMap
	@FunctionKey(RandomGenerate)
	public static List<TransitionLogic> provideRandomGenerateLogic(final RandomGenerateTransitionLogic randomGenerateLogic) {
		return List.of(randomGenerateLogic);
	}

	private UtilLogicModule() {
		throw new UnsupportedOperationException("Dagger2 module");
	}
}
