package com.hedera.services.sigs.metadata.lookups;

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

import com.hedera.services.config.FileNumbers;
import com.hedera.services.config.MockFileNumbers;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.HederaFs;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FileID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class HfsSigMetaLookupTest {
	private final FileNumbers fileNumbers = new MockFileNumbers();

	private final FileID target = IdUtils.asFile("0.0.12345");
	private final FileID specialTarget = IdUtils.asFile("0.0.150");

	private JKey wacl;
	private HFileMeta info;
	private HederaFs hfs;
	private HFileMeta immutableInfo;

	private HfsSigMetaLookup subject;

	@BeforeEach
	private void setup() throws Exception {
		wacl = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey();
		info = new HFileMeta(false, wacl, 1_234_567L);
		immutableInfo = new HFileMeta(false, StateView.EMPTY_WACL, 1_234_567L);

		hfs = mock(HederaFs.class);

		subject = new HfsSigMetaLookup(hfs, fileNumbers);
	}

	@Test
	void getsExpectedSigMetaForNormal() {
		given(hfs.exists(target)).willReturn(true);
		given(hfs.getattr(target)).willReturn(info);

		// when:
		var safeSigMeta = subject.safeLookup(target);

		// then:
		assertTrue(safeSigMeta.succeeded());
		assertEquals(wacl.toString(), safeSigMeta.metadata().wacl().toString());
	}

	@Test
	void getsExpectedSigMetaForSpecial() {
		given(hfs.exists(specialTarget)).willReturn(true);

		// when:
		var safeSigMeta = subject.safeLookup(specialTarget);

		// then:
		assertTrue(safeSigMeta.succeeded());
		assertSame(StateView.EMPTY_WACL, safeSigMeta.metadata().wacl());
	}

	@Test
	void omitsKeysForImmutableFile() {
		given(hfs.exists(target)).willReturn(true);
		given(hfs.getattr(target)).willReturn(immutableInfo);

		// when:
		var safeSigMeta = subject.safeLookup(target);

		// then:
		assertTrue(safeSigMeta.succeeded());
		assertTrue(safeSigMeta.metadata().wacl().isEmpty());
	}

	@Test
	void throwsExpectedType() {
		given(hfs.getattr(target)).willReturn(null);

		// when:
		var safeSigMeta = subject.safeLookup(target);

		// expect:
		Assertions.assertFalse(safeSigMeta.succeeded());
	}
}
