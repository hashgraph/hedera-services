package com.hedera.services;

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

import com.hedera.test.utils.ClassLoaderHelper;
import com.swirlds.platform.SignedStateFileManager;
import com.swirlds.platform.state.SignedState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

/**
 * These tests are responsible for testing loading of signed state data generated for various scenarios from various
 * tagged versions of the code.
 *
 * NOTE: If you see a failure in these tests, it means a change was made to the de-serialization path causing the load to
 * fail. Please double-check that a change made to the de-serialization code path is not adversely affecting decoding of
 * previous saved serialized byte data. Also, make sure that you have fully read out all bytes to de-serialize and not
 * leaving remaining bytes in the stream to decode.
 */
public class ServicesStateE2ETest {
	private final String signedStateDir = "src/test/resources/signedState/";

	@BeforeAll
	public static void setUp() {
		ClassLoaderHelper.loadClassPathDependencies();
	}

	@Test
	void testNftsFromSignedStateV25() throws IOException {
		loadSignedState(signedStateDir + "v0.25.3/SignedState.swh");
	}

	private static SignedState loadSignedState(final String path) throws IOException {
		var signedPair = SignedStateFileManager.readSignedStateFromFile(new File(path));
		// Because it's possible we are loading old data, we cannot check equivalence of the hash.
		Assertions.assertNotNull(signedPair.getRight());
		return signedPair.getRight();
	}
}
