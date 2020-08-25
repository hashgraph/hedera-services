package com.hedera.services.queries.contract;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.queries.consensus.GetTopicInfoAnswer;
import com.hedera.services.queries.consensus.HcsAnswers;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class ContractAnswersTest {
	GetBytecodeAnswer getBytecodeAnswer = mock(GetBytecodeAnswer.class);

	ContractAnswers subject;

	@Test
	public void hasExpectedAnswers() {
		// given:
		subject = new ContractAnswers(getBytecodeAnswer);

		// then:
		assertEquals(getBytecodeAnswer, subject.bytecodeAnswer());
	}
}
