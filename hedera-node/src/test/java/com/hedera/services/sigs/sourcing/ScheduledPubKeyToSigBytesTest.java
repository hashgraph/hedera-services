package com.hedera.services.sigs.sourcing;

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

import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
public class ScheduledPubKeyToSigBytesTest {
	byte[] key = "Putative signatory...".getBytes();
	byte[] expectedSig = "Anything else a great disappointment!".getBytes();

	private PubKeyToSigBytes scoped;
	private PubKeyToSigBytes scheduled;

	ScheduledPubKeyToSigBytes subject;

	@BeforeEach
	public void setup() throws Exception {
		scoped = mock(PubKeyToSigBytes.class);
		scheduled = mock(PubKeyToSigBytes.class);

		subject = new ScheduledPubKeyToSigBytes(scoped, scheduled);
	}

	@Test
	public void usesScopedDelegateForStd() throws Exception {
		given(scoped.sigBytesFor(key)).willReturn(expectedSig);

		// when:
		var actualSig = subject.sigBytesFor(key);

		// then:
		assertArrayEquals(expectedSig, actualSig);
	}

	@Test
	public void usesScheduledAsApropos() throws Exception {
		given(scheduled.sigBytesFor(key)).willReturn(expectedSig);

		// when:
		var actualSig = subject.sigBytesForScheduled(key);

		// then:
		assertArrayEquals(expectedSig, actualSig);
	}

	@Test
	public void returnsEmptySigOnAmbiguousPrefix() throws Exception {
		given(scheduled.sigBytesFor(key)).willThrow(KeyPrefixMismatchException.class);

		// when:
		var actualSig = subject.sigBytesForScheduled(key);

		// then:
		assertArrayEquals(PubKeyToSigBytes.EMPTY_SIG, actualSig);
	}
}
