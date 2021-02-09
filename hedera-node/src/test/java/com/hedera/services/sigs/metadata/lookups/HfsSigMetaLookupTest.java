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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.files.HederaFs;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FileID;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;

class HfsSigMetaLookupTest {
	HederaFs hfs;

	FileID target = IdUtils.asFile("0.0.12345");
	JKey wacl;
	HFileMeta info;
	HFileMeta immutableInfo;
	HfsSigMetaLookup subject;

	@BeforeEach
	private void setup() throws Exception {
		wacl = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey();
		info = new HFileMeta(false, wacl, 1_234_567L);
		immutableInfo = new HFileMeta(false, StateView.EMPTY_WACL, 1_234_567L);

		hfs = mock(HederaFs.class);

		subject = new HfsSigMetaLookup(hfs);
	}

	@Test
	public void getsExpectedSigMeta() throws Exception {
		given(hfs.exists(target)).willReturn(true);
		given(hfs.getattr(target)).willReturn(info);

		// when:
		var safeSigMeta = subject.safeLookup(target);

		// then:
		assertTrue(safeSigMeta.succeeded());
		assertEquals(wacl.toString(), safeSigMeta.metadata().getWacl().toString());
	}

	@Test
	public void omitsKeysForImmutableFile() {
		given(hfs.exists(target)).willReturn(true);
		given(hfs.getattr(target)).willReturn(immutableInfo);

		// when:
		var safeSigMeta = subject.safeLookup(target);

		// then:
		assertTrue(safeSigMeta.succeeded());
		assertTrue(safeSigMeta.metadata().getWacl().isEmpty());
	}

	@Test
	public void throwsExpectedType() {
		given(hfs.getattr(target)).willReturn(null);

		// when:
		var safeSigMeta = subject.safeLookup(target);

		// expect:
		Assertions.assertFalse(safeSigMeta.succeeded());
	}
}
