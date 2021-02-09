package com.hedera.services.keys;

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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEd25519KeyReaderTest {
	private final String expectedABytes = "447dc6bdbfc64eb894851825194744662afcb70efb8b23a6a24af98f0c1fd8ad";
	private final String b64Loc = "src/test/resources/bootstrap/PretendStartupAccount.txt";
	private final String invalidB64Loc = "src/test/resources/bootstrap/PretendStartupAccount.txt";

	LegacyEd25519KeyReader subject = new LegacyEd25519KeyReader();

	@Test
	public void getsExpectedABytes() {
		// expect:
		assertEquals(
				expectedABytes,
				subject.hexedABytesFrom(b64Loc, "START_ACCOUNT"));
	}

	@Test
	public void throwsIaeOnProblem() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.hexedABytesFrom(invalidB64Loc, "NOPE"));
	}
}
