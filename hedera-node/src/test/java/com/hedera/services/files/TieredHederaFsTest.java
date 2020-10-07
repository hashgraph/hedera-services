package com.hedera.services.files;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hedera.services.legacy.core.jproto.JKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.AbstractMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.files.TieredHederaFs.BYTES_PER_KB;
import com.hedera.services.files.TieredHederaFs.IllegalArgumentType;
import org.mockito.InOrder;

@RunWith(JUnitPlatform.class)
class TieredHederaFsTest {
	final Instant now = Instant.now();

	int lifetimeSecs = 1_234_567;
	JKey validKey;
	byte[] origContents = "Where, like a pillow on a bed /".getBytes();
	byte[] newContents = "Where, like a pillow on a bed / A pregnant bank swelled up to rest /".getBytes();
	byte[] moreContents = "The violet's reclining head".getBytes();
	JFileInfo deadAttr;
	JFileInfo livingAttr;
	JFileInfo deletedAttr;
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
	Map<FileID, JFileInfo> metadata;
	SpecialFileSystem specialFileSystem;
	TieredHederaFs subject;

	@BeforeEach
	private void setup() throws Throwable {
		validKey = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey();
		deadAttr = new JFileInfo(false, validKey, now.getEpochSecond() - 1);
		livingAttr = new JFileInfo(false, validKey, now.getEpochSecond() + lifetimeSecs);
		deletedAttr = new JFileInfo(true, validKey, now.getEpochSecond() + lifetimeSecs);

		noInterceptor = mock(FileUpdateInterceptor.class);
		given(noInterceptor.priorityForCandidate(any())).willReturn(OptionalInt.empty());

		lowInterceptor = mock(FileUpdateInterceptor.class);
		given(lowInterceptor.priorityForCandidate(any())).willReturn(OptionalInt.of(Integer.MAX_VALUE));
		highInterceptor = mock(FileUpdateInterceptor.class);
		given(highInterceptor.priorityForCandidate(any())).willReturn(OptionalInt.of(Integer.MIN_VALUE));

		ids = mock(EntityIdSource.class);
		data = mock(Map.class);
		metadata = mock(Map.class);
		specialFileSystem = mock(SpecialFileSystem.class);

		clock = mock(Supplier.class);
		given(clock.get()).willReturn(now);

		properties = mock(GlobalDynamicProperties.class);
		given(properties.maxFileSizeKb()).willReturn(1);

		subject = new TieredHederaFs(ids, properties, clock, data, metadata, this::getCurrentSpecialFileSystem);
	}

	private SpecialFileSystem getCurrentSpecialFileSystem() {
		return specialFileSystem;
	}

	@Test
	public void registerWorks() {
		// when:
		subject.register(lowInterceptor);
		// and:
		subject.register(highInterceptor);

		// then:
		assertEquals(lowInterceptor, subject.updateInterceptors.get(0));
		assertEquals(highInterceptor, subject.updateInterceptors.get(1));
	}

	@Test
	public void appendsWithExpectedResultSansInterception() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);
		// and:
		given(data.get(fid)).willReturn(origContents);

		// when:
		var result = subject.append(fid, moreContents);

		// then:
		assertEquals(SUCCESS, result.outcome());
		assertTrue(result.fileReplaced());
		// and:
		verify(data).put(
				argThat(fid::equals),
				argThat(bytes -> new String(bytes).equals(
						new String(origContents) + new String(moreContents)
				)));
	}

	@Test
	public void overwritesWithExpectedResultSansInterception() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		var result = subject.overwrite(fid, newContents);

		// then:
		assertEquals(SUCCESS, result.outcome());
		assertTrue(result.fileReplaced());
		// and:
		verify(data).put(fid, newContents);
	}

	@Test
	public void highPriorityInterceptorSetsOutcome() {
		given(highInterceptor.preUpdate(fid, newContents))
				.willReturn(
						new AbstractMap.SimpleEntry<>(ResponseCodeEnum.FAIL_FEE, true));
		given(lowInterceptor.preUpdate(fid, newContents)).willReturn(
				new AbstractMap.SimpleEntry<>(ResponseCodeEnum.FAIL_INVALID, false));
		// and:
		subject.register(lowInterceptor);
		subject.register(highInterceptor);
		// and:
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		var result = subject.overwrite(fid, newContents);

		// then:
		assertEquals(ResponseCodeEnum.FAIL_FEE, result.outcome());
		assertFalse(result.fileReplaced());
		// and:
		verify(data, never()).put(fid, newContents);
	}

	@Test
	public void relevantInterceptorsGetPostCb() {
		InOrder inOrder = inOrder(highInterceptor, lowInterceptor);

		given(highInterceptor.preUpdate(fid, newContents))
				.willReturn(
						new AbstractMap.SimpleEntry<>(ResponseCodeEnum.OK, true));
		given(lowInterceptor.preUpdate(fid, newContents)).willReturn(
				new AbstractMap.SimpleEntry<>(ResponseCodeEnum.OK, true));
		// and:
		subject.register(lowInterceptor);
		subject.register(highInterceptor);
		// and:
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		var result = subject.overwrite(fid, newContents);

		// then:
		assertEquals(ResponseCodeEnum.OK, result.outcome());
		assertTrue(result.fileReplaced());
		// and:
		verify(data).put(fid, newContents);
		// and:
		inOrder.verify(highInterceptor).postUpdate(fid, newContents);
		inOrder.verify(lowInterceptor).postUpdate(fid, newContents);
	}

	@Test
	public void shortCircuitsIfInterceptorRejects() {
		given(highInterceptor.preUpdate(fid, newContents))
				.willReturn(
						new AbstractMap.SimpleEntry<>(ResponseCodeEnum.AUTHORIZATION_FAILED, false));
		given(lowInterceptor.preUpdate(fid, newContents)).willReturn(
				new AbstractMap.SimpleEntry<>(ResponseCodeEnum.OK, true));
		// and:
		subject.register(lowInterceptor);
		subject.register(highInterceptor);
		// and:
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		var result = subject.overwrite(fid, newContents);

		// then:
		assertEquals(ResponseCodeEnum.AUTHORIZATION_FAILED, result.outcome());
		assertFalse(result.fileReplaced());
		// and:
		verify(data, never()).put(fid, newContents);
	}

	@Test
	public void appendRejectsOversizeContents() {
		// setup:
		IllegalArgumentException iae = null;
		var stretchContents = new byte[BYTES_PER_KB - 1];
		var burstContents = new byte[2];

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);
		given(data.get(fid)).willReturn(stretchContents);
		// and:
		given(properties.maxFileSizeKb()).willReturn(1);

		// when:
		try {
			subject.append(fid, burstContents);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.OVERSIZE_CONTENTS,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void overwriteRejectsOversizeContents() {
		// setup:
		IllegalArgumentException iae = null;
		var oversizeContents = new byte[BYTES_PER_KB + 1];

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);
		// and:
		given(properties.maxFileSizeKb()).willReturn(1);

		// when:
		try {
			subject.overwrite(fid, oversizeContents);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.OVERSIZE_CONTENTS,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void rmThrowsOnMissing() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(missing)).willReturn(false);

		// when:
		try {
			subject.rm(missing);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.UNKNOWN_FILE,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void rmPurgesAsExpected() {
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
	public void deleteThrowsOnMissing() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(missing)).willReturn(false);

		// when:
		try {
			subject.delete(missing);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.UNKNOWN_FILE,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void deleteThrowsOnDeleted() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// when:
		try {
			subject.delete(fid);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.DELETED_FILE,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void deleteRespectsInterceptors() {
		// setup:
		FileUpdateInterceptor authPolicy = mock(FileUpdateInterceptor.class);
		given(authPolicy.priorityForCandidate(fid)).willReturn(OptionalInt.of(Integer.MIN_VALUE));
		given(authPolicy.preDelete(fid)).willReturn(new AbstractMap.SimpleEntry<>(AUTHORIZATION_FAILED, false));

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		subject.register(authPolicy);
		var result = subject.delete(fid);

		// then:
		verify(metadata, never()).put(argThat(fid::equals), any());
		verify(data, never()).remove(fid);
		// and:
		assertFalse(result.attrChanged());
		assertFalse(result.fileReplaced());
		assertEquals(AUTHORIZATION_FAILED, result.outcome());
	}

	@Test
	public void deleteUpdatesAttrAsExpected() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		var result = subject.delete(fid);

		// then:
		verify(metadata).put(argThat(fid::equals), argThat(attr ->
			attr.isDeleted() &&
					attr.getExpirationTimeSeconds()	== livingAttr.getExpirationTimeSeconds() &&
					attr.getWacl().equals(livingAttr.getWacl())));
		verify(data).remove(fid);
		// and:
		assertTrue(result.attrChanged());
		assertTrue(result.fileReplaced());
		assertEquals(SUCCESS, result.outcome());
	}

	@Test
	public void appendThrowsOnMissing() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(missing)).willReturn(false);

		// when:
		try {
			subject.append(missing, moreContents);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.UNKNOWN_FILE,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void overwriteThrowsOnMissing() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(missing)).willReturn(false);

		// when:
		try {
			subject.overwrite(missing, newContents);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.UNKNOWN_FILE,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void appendThrowsOnDeleted() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// when:
		try {
			subject.append(fid, moreContents);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.DELETED_FILE,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void overwriteThrowsOnDeleted() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// when:
		try {
			subject.overwrite(fid, newContents);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.DELETED_FILE,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void lsThrowsOnMissing() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(missing)).willReturn(false);

		// when:
		try {
			subject.getattr(missing);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.UNKNOWN_FILE,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void lsGetsExpected() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// when:
		var meta = subject.getattr(fid);

		// then:
		assertEquals(deletedAttr, meta);
	}

	@Test
	public void catThrowsOnMissing() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(missing)).willReturn(false);

		// when:
		try {
			subject.cat(missing);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.UNKNOWN_FILE,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void catThrowsOnDeleted() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// when:
		try {
			subject.cat(fid);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.DELETED_FILE,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void catGetsExpected() {
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
	public void usesMetadataToCheckExistence() {
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
	public void sudoSetattrAllowsDeletedFile() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// when:
		var result = subject.sudoSetattr(fid, livingAttr);

		// then:
		verify(metadata).put(fid, livingAttr);
		assertTrue(result.attrChanged());
		assertFalse(result.fileReplaced());
		assertEquals(SUCCESS, result.outcome());
	}

	@Test
	public void setattrRejectsDeletedFile() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(deletedAttr);

		// when:
		try {
			subject.setattr(fid, livingAttr);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.DELETED_FILE,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void sudoSetattrRejectsMissingFile() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(missing)).willReturn(false);

		// when:
		try {
			subject.sudoSetattr(missing, livingAttr);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.UNKNOWN_FILE,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}


	@Test
	public void setattrRejectsMissingFile() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(missing)).willReturn(false);

		// when:
		try {
			subject.setattr(missing, livingAttr);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.UNKNOWN_FILE,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void setattrUpdatesAsExpected() {
		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		var result = subject.setattr(fid, livingAttr);

		// then:
		verify(metadata).put(fid, livingAttr);
		assertTrue(result.attrChanged());
		assertFalse(result.fileReplaced());
		assertEquals(SUCCESS, result.outcome());
	}

	@Test
	public void setattrRespectsInterceptors() {
		// setup:
		FileUpdateInterceptor authPolicy = mock(FileUpdateInterceptor.class);
		given(authPolicy.priorityForCandidate(fid)).willReturn(OptionalInt.of(Integer.MIN_VALUE));
		given(authPolicy.preAttrChange(argThat(fid::equals), any()))
				.willReturn(new AbstractMap.SimpleEntry<>(AUTHORIZATION_FAILED, false));

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		subject.register(authPolicy);
		var result = subject.setattr(fid, livingAttr);

		// then:
		verify(metadata, never()).put(fid, livingAttr);
		assertFalse(result.attrChanged());
		assertFalse(result.fileReplaced());
		assertEquals(AUTHORIZATION_FAILED, result.outcome());
	}

	@Test
	public void setattrRejectsExpiredFile() {
		// setup:
		IllegalArgumentException iae = null;

		given(metadata.containsKey(fid)).willReturn(true);
		given(metadata.get(fid)).willReturn(livingAttr);

		// when:
		try {
			subject.setattr(fid, deadAttr);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.FILE_WOULD_BE_EXPIRED,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void createRejectsExpiredFile() {
		// given:
		IllegalArgumentException iae = null;

		// when:
		try {
			subject.create(origContents, deadAttr, sponsor);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.FILE_WOULD_BE_EXPIRED,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void createRejectsOversizeContents() {
		// setup:
		IllegalArgumentException iae = null;

		given(properties.maxFileSizeKb()).willReturn(1);
		// and:
		var oversizeContents = new byte[BYTES_PER_KB + 1];

		// when:
		try {
			subject.create(oversizeContents, livingAttr, sponsor);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertEquals(
				IllegalArgumentType.OVERSIZE_CONTENTS,
				IllegalArgumentType.valueOf(iae.getMessage()));
	}

	@Test
	public void createUsesNextEntityId() {
		given(ids.newFileId(sponsor)).willReturn(fid);

		// when:
		var newFile = subject.create(origContents, livingAttr, sponsor);

		// then:
		assertEquals(fid, newFile);
		verify(data).put(fid, origContents);
		verify(metadata).put(fid, livingAttr);
	}
}
