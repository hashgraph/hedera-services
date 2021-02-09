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

import com.hedera.services.files.HederaFs;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.TransactionSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.keys.DefaultActivationCharacteristics.DEFAULT_ACTIVATION_CHARACTERISTICS;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.function.Function;

@RunWith(JUnitPlatform.class)
class CharacteristicsFactoryTest {
	FileID target = IdUtils.asFile("0.0.75231");
	FileID missing = IdUtils.asFile("1.2.3");
	JKeyList wacl = new JKeyList(List.of(new JEd25519Key("NOPE".getBytes())));
	JFileInfo info = new JFileInfo(false, wacl, 1_234_567L);

	HederaFs hfs;
	CharacteristicsFactory subject;
	KeyActivationCharacteristics revocationServiceCharacteristics;
	Function<JKeyList, KeyActivationCharacteristics> revocationServiceCharacteristicsFn;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		hfs = mock(HederaFs.class);
		given(hfs.exists(target)).willReturn(true);
		given(hfs.getattr(target)).willReturn(info);

		revocationServiceCharacteristics = mock(KeyActivationCharacteristics.class);
		revocationServiceCharacteristicsFn = (Function<JKeyList, KeyActivationCharacteristics>) mock(Function.class);
		given(revocationServiceCharacteristicsFn.apply(wacl)).willReturn(revocationServiceCharacteristics);

		subject = new CharacteristicsFactory(hfs);

		CharacteristicsFactory.revocationServiceCharacteristicsFn = revocationServiceCharacteristicsFn;
	}

	@AfterEach
	void cleanup() {
		CharacteristicsFactory.revocationServiceCharacteristicsFn = RevocationServiceCharacteristics::forTopLevelFile;
	}

	@Test
	public void usesDefaultForNonFileDelete() {
		// expect:
		assertSame(DEFAULT_ACTIVATION_CHARACTERISTICS, subject.inferredFor(nonFileDelete()));
	}

	@Test
	public void usesDefaultForMalformedFileDelete() {
		// expect:
		assertSame(DEFAULT_ACTIVATION_CHARACTERISTICS, subject.inferredFor(meaninglessFileDelete()));
		assertSame(DEFAULT_ACTIVATION_CHARACTERISTICS, subject.inferredFor(missingFileDelete()));
	}

	@Test
	public void usesAproposForFileDelete() {
		// expect:
		assertSame(revocationServiceCharacteristics, subject.inferredFor(fileDelete()));
	}

	private TransactionBody nonFileDelete() {
		return TransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
				.build();
	}

	private TransactionBody meaninglessFileDelete() {
		return TransactionBody.newBuilder()
				.setFileDelete(FileDeleteTransactionBody.getDefaultInstance())
				.build();
	}

	private TransactionBody missingFileDelete() {
		return TransactionBody.newBuilder()
				.setFileDelete(FileDeleteTransactionBody.newBuilder()
						.setFileID(missing))
				.build();
	}

	private TransactionBody fileDelete() {
		return TransactionBody.newBuilder()
				.setFileDelete(FileDeleteTransactionBody.newBuilder()
					.setFileID(target))
				.build();
	}
}
