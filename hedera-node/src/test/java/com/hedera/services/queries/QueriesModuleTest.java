package com.hedera.services.queries;

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

import com.hedera.services.context.ServicesNodeType;
import com.hedera.services.queries.answering.ZeroStakeAnswerFlow;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

class QueriesModuleTest {
	@Test
	void usesZeroStakeWhenAppropriate() {
		// expect:
		assertThat(QueriesModule.provideAnswerFlow(
			null,
			null,
			null,
			ServicesNodeType.ZERO_STAKE_NODE,
			null,
			null,
			null,
			null,
			null,
			null,
			null
		), instanceOf(ZeroStakeAnswerFlow.class));
	}
}
