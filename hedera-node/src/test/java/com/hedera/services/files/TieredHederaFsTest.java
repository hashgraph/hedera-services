package com.hedera.services.files;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.AbstractMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Supplier;

import static com.hedera.services.files.TieredHederaFs.BYTES_PER_KB;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

class TieredHederaFsTest {
	final Instant now = Instant.now();

	int lifetimeSecs = 1_234_567;
	JKey validKey;
	byte[] origContents = "Where, like a pillow on a bed /".getBytes();
	byte[] newContents = "Where, like a pillow on a bed / A pregnant bank swelled up to rest /".getBytes();
	byte[] moreContents = "The violet's reclining head".getBytes();
	HFileMeta deadAttr;
	HFileMeta livingAttr;
	HFileMeta deletedAttr;
	FileID fid = IdUtils.asFile("0.0.7575");
	FileID missing = IdUtils.asFile("0.0.666");
	AccountID sponsor = IdUtils.asAccount("0.0.2");

	FileUpdateInterceptor noInterceptor;
	FileUpdateInterceptor lowInterceptor;
	FileUpdateInterceptor highInterceptor;

	EntityIdSource ids;
	GlobalDynamicProperties properties;
	Supplier<Instant> clock;
	Map<FileID, byte[]> data;
	Map<FileID, HFileMeta> metadata;
	MerkleDiskFs diskFs;
	TieredHederaFs subject;

	@BeforeEach
	private void setup() throws Throwable {
		validKey = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey();
		deadAttr = new HFileMeta(false, validKey, now.getEpochSecond() - 1);
		livingAttr = new HFileMeta(false, validKey, now.getEpochSecond() + lifetimeSecs);
		deletedAttr = new HFileMeta(true, validKey, now.getEpochSecond() + lifetimeSecs);

		noInterceptor = mock(FileUpdateInterceptor.class);
		given(noInterceptor.priorityForCandidate(any())).willReturn(OptionalInt.empty());

		lowInterceptor = mock(FileUpdateInterceptor.class);
		given(lowInterceptor.priorityForCandidate(any())).willReturn(OptionalInt.of(Integer.MAX_VALUE));
		highInterceptor = mock(FileUpdateInterceptor.class);
		given(highInterceptor.priorityForCandidate(any())).willReturn(OptionalInt.of(Integer.MIN_VALUE));

		ids = mock(EntityIdSource.class);
		data = mock(Map.class);
		metadata = mock(Map.class);
		diskFs = mock(MerkleDiskFs.class);

		clock = mock(Supplier.class);
		given(clock.get()).willReturn(now);

		properties = mock(GlobalDynamicProperties.class);
		given(properties.maxFileSizeKb()).willReturn(1);

		subject = new TieredHederaFs(ids, properties, clock, data, metadata, () -> diskFs);
	}

	@Test
	void interceptorsAreRegistered() {
		// given:
		subject.register(lowInterceptor);
		subject.register(highInterceptor);

		// expect:
		assertEquals(lowInterceptor, subject.updateInterceptors.get(0));
		assertEquals(highInterceptor, subject.updateInterceptors.get(1));
		// and:
		assertEquals(2, subject.numRegisteredInterceptors());
	}

	@Test
	void appendsWithExpectedResultSansInterception() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);
		// and:
		given(data.get(fid)).willReturn(origContents);

		// when:
		subject.append(fid, moreContents);

		// and:
		verify(data).put(
				argThat(fid::equals),
				argThat(bytes -> new String(bytes).equals(
						new String(origContents) + new String(moreContents)
				)));
	}

	@Test
	void overwritesWithExpectedResultSansInterception() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		subject.overwrite(fid, newContents);

		// then:
		verify(data).put(fid, newContents);
	}

	@Test
	void highPriorityInterceptorSetsOutcome() {
		given(highInterceptor.preUpdate(fid, newContents))
				.willReturn(
						new AbstractMap.SimpleEntry<>(FAIL_FEE, true));
		given(lowInterceptor.preUpdate(fid, newContents)).willReturn(
				new AbstractMap.SimpleEntry<>(ResponseCodeEnum.FAIL_INVALID, false));
		// and:
		subject.register(lowInterceptor);
		subject.register(highInterceptor);
		// and:
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		assertFailsWith(() -> subject.overwrite(fid, newContents), FAIL_FEE);

		// then:
		verify(data, never()).put(fid, newContents);
	}

	@Test
	void relevantInterceptorsGetPostCb() {
		InOrder inOrder = inOrder(highInterceptor, lowInterceptor);

		given(highInterceptor.preUpdate(fid, newContents))
				.willReturn(
						new AbstractMap.SimpleEntry<>(ResponseCodeEnum.SUCCESS, true));
		given(lowInterceptor.preUpdate(fid, newContents)).willReturn(
				new AbstractMap.SimpleEntry<>(ResponseCodeEnum.SUCCESS, true));
		// and:
		subject.register(lowInterceptor);
		subject.register(highInterceptor);
		// and:
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		subject.overwrite(fid, newContents);

		// then:
		verify(data).put(fid, newContents);
		// and:
		inOrder.verify(highInterceptor).postUpdate(fid, newContents);
		inOrder.verify(lowInterceptor).postUpdate(fid, newContents);
	}

	@Test
	void shortCircuitsIfInterceptorRejects() {
		given(highInterceptor.preUpdate(fid, newContents))
				.willReturn(
						new AbstractMap.SimpleEntry<>(AUTHORIZATION_FAILED, false));
		given(lowInterceptor.preUpdate(fid, newContents)).willReturn(
				new AbstractMap.SimpleEntry<>(ResponseCodeEnum.OK, true));
		// and:
		subject.register(lowInterceptor);
		subject.register(highInterceptor);
		// and:
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		assertFailsWith(() -> subject.overwrite(fid, newContents), AUTHORIZATION_FAILED);

		// then:
		verify(data, never()).put(fid, newContents);
	}

	@Test
	void appendRejectsOversizeContents() {
		// setup:
		var stretchContents = new byte[BYTES_PER_KB - 1];
		var burstContents = new byte[2];

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);
		given(data.get(fid)).willReturn(stretchContents);
		// and:
		given(properties.maxFileSizeKb()).willReturn(1);

		// then:
		assertFailsWith(() -> subject.append(fid, burstContents), MAX_FILE_SIZE_EXCEEDED);
	}

	@Test
	void appendAllowsOversizeContentsForDiskFs() {
		// setup:
		var stretchContents = new byte[BYTES_PER_KB - 1];
		var burstContents = new byte[2];

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);
		given(diskFs.contains(fid)).willReturn(true);
		given(diskFs.contentsOf(fid)).willReturn(stretchContents);
		// and:
		given(properties.maxFileSizeKb()).willReturn(1);

		// when:
		subject.append(fid, burstContents);

		// then:
		verify(diskFs).put(
				argThat(fid::equals),
				argThat(bytes -> new String(bytes).equals(
						new String(stretchContents) + new String(burstContents)
				)));
	}

	@Test
	void overwritePermitsOversizeContentsForDiskFs() {
		// setup:
		var oversizeContents = new byte[BYTES_PER_KB + 1];

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);
		given(diskFs.contains(fid)).willReturn(true);
		// and:
		given(properties.maxFileSizeKb()).willReturn(1);

		// when:
		subject.overwrite(fid, oversizeContents);

		// then:
		verify(diskFs).put(
				argThat(fid::equals),
				argThat(bytes -> new String(bytes).equals(
						new String(oversizeContents)
				)));
	}

	@Test
	void overwriteRejectsOversizeContents() {
		// setup:
		var oversizeContents = new byte[BYTES_PER_KB + 1];

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);
		// and:
		given(properties.maxFileSizeKb()).willReturn(1);

		// then:
        assertFailsWith(() -> subject.overwrite(fid, oversizeContents), MAX_FILE_SIZE_EXCEEDED);
	}

	@Test
	void rmThrowsOnMissing() {
		// setup:
		given(metadata.containsKey(missing)).willReturn(false);

		// then:
        assertFailsWith(() -> subject.rm(missing), INVALID_FILE_ID);
	}

	@Test
	void rmPurgesAsExpected() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);
		// and:
		given(data.containsKey(fid)).willReturn(true);

		// when:
		subject.rm(fid);

		// then:
		verify(metadata).remove(fid);
		verify(data).remove(fid);
	}

	@Test
	void deleteThrowsOnMissing() {
		// setup:
		given(metadata.containsKey(missing)).willReturn(false);

		// then:
        assertFailsWith(() -> subject.delete(missing), INVALID_FILE_ID);
    }

	@Test
	void deleteThrowsOnDeleted() {
		// setup:
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// then:
        assertFailsWith(() -> subject.delete(fid), FILE_DELETED);
	}

	@Test
	void deleteRespectsInterceptors() {
		// setup:
		FileUpdateInterceptor authPolicy = mock(FileUpdateInterceptor.class);
		given(authPolicy.priorityForCandidate(fid)).willReturn(OptionalInt.of(Integer.MIN_VALUE));
		given(authPolicy.preDelete(fid)).willReturn(new AbstractMap.SimpleEntry<>(AUTHORIZATION_FAILED, false));

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		subject.register(authPolicy);
		assertFailsWith(() -> subject.delete(fid), AUTHORIZATION_FAILED);

		// then:
		verify(metadata, never()).put(argThat(fid::equals), any());
		verify(data, never()).remove(fid);
	}

	@Test
	void deleteUpdatesAttrAsExpected() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		subject.delete(fid);

		// then:
		verify(metadata).put(argThat(fid::equals), argThat(attr ->
			attr.isDeleted() &&
					attr.getExpiry()	== livingAttr.getExpiry() &&
					attr.getWacl().equals(livingAttr.getWacl())));
		verify(data).remove(fid);
	}

	@Test
	void appendThrowsOnMissing() {
		// setup:
		given(metadata.containsKey(missing)).willReturn(false);

		// then:
		assertFailsWith(() -> subject.append(missing, moreContents), INVALID_FILE_ID);
	}

	@Test
	void overwriteThrowsOnMissing() {
		// setup:
		given(metadata.containsKey(missing)).willReturn(false);

		// then:
        assertFailsWith(() -> subject.overwrite(missing, newContents), INVALID_FILE_ID);
	}

	@Test
	void appendThrowsOnDeleted() {
		// setup:
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// then:
		assertFailsWith(() -> subject.append(fid, moreContents), FILE_DELETED);
	}

	@Test
	void overwriteThrowsOnDeleted() {
		// setup:
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// then:
        assertFailsWith(() -> subject.overwrite(fid, newContents), FILE_DELETED);
	}

	@Test
	void lsThrowsOnMissing() {
		// setup:
		given(metadata.containsKey(missing)).willReturn(false);

		// then:
        assertFailsWith(() -> subject.getattr(missing), INVALID_FILE_ID);
    }

	@Test
	void lsGetsExpected() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// when:
		var meta = subject.getattr(fid);

		// then:
		assertEquals(deletedAttr, meta);
	}

	@Test
	void catThrowsOnMissing() {
		// setup:
		given(metadata.containsKey(missing)).willReturn(false);

		// then:
        assertFailsWith(() -> subject.cat(missing), INVALID_FILE_ID);
    }

	@Test
	void catThrowsOnDeleted() {
		// setup:
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// then:
        assertFailsWith(() -> subject.cat(fid), FILE_DELETED);
    }

	@Test
	void catGetsExpected() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);
		given(data.get(fid)).willReturn(origContents);

		// when:
		var contents = subject.cat(fid);

		// then:
		assertEquals(
				new String(origContents),
				new String(contents));
	}

	@Test
	void usesMetadataToCheckExistence() {
		given(metadata.containsKey(fid)).willReturn(true);

		// when:
		boolean yesFlag = subject.exists(fid);
		// and:
		boolean noFlag = subject.exists(missing);

		// then:
		assertTrue(yesFlag);
		assertFalse(noFlag);
		// and:
		verify(metadata).containsKey(fid);
		verify(metadata).containsKey(missing);
	}

	@Test
	void sudoSetattrAllowsDeletedFile() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// when:
		subject.sudoSetattr(fid, livingAttr);

		// then:
		verify(metadata).put(fid, livingAttr);
	}

	@Test
	void setattrRejectsDeletedFile() {
		// setup:
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// then:
        assertFailsWith(() -> subject.setattr(fid, livingAttr), FILE_DELETED);
    }

	@Test
	void sudoSetattrRejectsMissingFile() {
		// setup:
		given(metadata.containsKey(missing)).willReturn(false);

        assertFailsWith(() -> subject.sudoSetattr(missing, livingAttr), INVALID_FILE_ID);
    }


	@Test
	void setattrRejectsMissingFile() {
		// setup:
		given(metadata.containsKey(missing)).willReturn(false);

		// then:
        assertFailsWith(() -> subject.sudoSetattr(missing, livingAttr), INVALID_FILE_ID);
    }

	@Test
	void setattrUpdatesAsExpected() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		subject.setattr(fid, livingAttr);

		// then:
		verify(metadata).put(fid, livingAttr);
	}

	@Test
	void setattrRespectsInterceptors() {
		// setup:
		FileUpdateInterceptor authPolicy = mock(FileUpdateInterceptor.class);
		given(authPolicy.priorityForCandidate(fid)).willReturn(OptionalInt.of(Integer.MIN_VALUE));
		given(authPolicy.preAttrChange(argThat(fid::equals), any()))
				.willReturn(new AbstractMap.SimpleEntry<>(AUTHORIZATION_FAILED, false));

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		subject.register(authPolicy);
		assertFailsWith(() -> subject.setattr(fid, livingAttr), AUTHORIZATION_FAILED);

		// then:
		verify(metadata, never()).put(fid, livingAttr);
	}

	@Test
	void setattrRejectsExpiredFile() {
		// setup:
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// then:
        assertFailsWith(() -> subject.sudoSetattr(fid, deadAttr), INVALID_EXPIRATION_TIME);
    }

	@Test
	void createRejectsExpiredFile() {
		// given:
		// then:
        assertFailsWith(() -> subject.create(origContents, deadAttr, sponsor), INVALID_EXPIRATION_TIME);
    }

	@Test
	void createRejectsOversizeContents() {
		// setup:
		given(properties.maxFileSizeKb()).willReturn(1);
		// and:
		var oversizeContents = new byte[BYTES_PER_KB + 1];

		// then:
        assertFailsWith(() -> subject.create(oversizeContents, livingAttr, sponsor), MAX_FILE_SIZE_EXCEEDED);
    }

	@Test
	void createUsesNextEntityId() {
		given(ids.newFileId(sponsor)).willReturn(fid);

		// when:
		var newFile = subject.create(origContents, livingAttr, sponsor);

		// then:
		assertEquals(fid, newFile);
		verify(data).put(fid, origContents);
		verify(metadata).put(fid, livingAttr);
	}

	@Test
	void createNewFile150ThenReadAndAppend() {
		FileID fileID = FileID.newBuilder().setFileNum(150L).build();
		given(metadata.containsKey(fileID)).willReturn(true);
		given(metadata.get(fileID)).willReturn(livingAttr);
		given(diskFs.contains(fileID)).willReturn(true);
		given(diskFs.contentsOf(fileID)).willReturn(newContents);
		// when:
		subject.overwrite(fileID, newContents);

		// then:
		verify(diskFs).put(fileID, newContents);

		// and when:
		subject.append(fileID, moreContents);

		// then:
		verify(diskFs).put(fileID, (new String(newContents) + new String(moreContents)).getBytes());
	}
}
