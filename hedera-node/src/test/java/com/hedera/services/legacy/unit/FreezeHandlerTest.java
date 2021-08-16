package com.hedera.services.legacy.unit;

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

import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.files.HederaFs;
import com.hedera.services.legacy.handler.FreezeHandler;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldDualState;
import com.swirlds.common.internal.SettingsCommon;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;

import static com.hedera.services.legacy.unit.FreezeTestHelper.createFreezeTransaction;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.lang.Thread.sleep;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MINUTE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(LogCaptureExtension.class)
class FreezeHandlerTest {
	private Instant consensusTime = Instant.now();
	private HederaFs hfs;
	private Platform platform;
	private ExchangeRateSet rates;
	private HbarCentExchange exchange;
	private SwirldDualState dualState;

	@Inject
	private LogCaptor logCaptor;
	@LoggingSubject
	private FreezeHandler subject;

	@BeforeEach
	void setUp() {
		SettingsCommon.transactionMaxBytes = 1_234_567;

		hfs = mock(HederaFs.class);
		rates = mock(ExchangeRateSet.class);
		exchange = mock(HbarCentExchange.class);
		given(exchange.activeRates()).willReturn(rates);
		platform = Mockito.mock(Platform.class);
		given(platform.getSelfId()).willReturn(new NodeId(false, 1));
		dualState = mock(SwirldDualState.class);

		subject = new FreezeHandler(hfs, platform, exchange, () -> dualState);
	}

	@Test
	void useTimeStampAsFreezeStartTime() throws Exception {
		final var expectedStart = Instant.now().plusSeconds(100);
		final var transaction = createFreezeTransaction(true, true, null, expectedStart);
		final var txBody = CommonUtils.extractTransactionBody(transaction);

		final var record = subject.freeze(txBody, consensusTime);

		assertEquals(SUCCESS, record.getReceipt().getStatus());
		verify(dualState).setFreezeTime(expectedStart);
	}

	@Test
	void setsInstantInSameDayWhenNatural() throws Exception {
		final var transaction = createFreezeTransaction(true, true, null);
		final var txBody = CommonUtils.extractTransactionBody(transaction);
		final var nominalStartHour = txBody.getFreeze().getStartHour();
		final var nominalStartMin = txBody.getFreeze().getStartMin();
		final var expectedStart = naturalNextInstant(nominalStartHour, nominalStartMin, consensusTime);

		final var record = subject.freeze(txBody, consensusTime);

		assertEquals(SUCCESS, record.getReceipt().getStatus());
		verify(dualState).setFreezeTime(expectedStart);
	}

	@Test
	void setsInstantInNextDayWhenNatural() throws Exception {
		final var transaction = createFreezeTransaction(
				true,
				true,
				null,
				null,
				new int[] { 10, 0 },
				new int[] { 10, 1 },
				null);
		final var txBody = CommonUtils.extractTransactionBody(transaction);
		final var nominalStartHour = txBody.getFreeze().getStartHour();
		final var nominalStartMin = txBody.getFreeze().getStartMin();
		final var expectedStart = naturalNextInstant(nominalStartHour, nominalStartMin, consensusTime);

		final var record = subject.freeze(txBody, consensusTime);

		assertEquals(SUCCESS, record.getReceipt().getStatus());
		verify(dualState).setFreezeTime(expectedStart);
	}

	@Test
	void computesMinsSinceConsensusMidnight() {
		final var consensusNow = Instant.parse("2021-05-28T14:38:34.546097Z");
		final int minutesSinceMidnight = 14 * 60 + 38;

		assertEquals(minutesSinceMidnight, subject.minutesSinceMidnight(consensusNow));
	}

	private Instant naturalNextInstant(final int nominalHour, final int nominalMin, final Instant now) {
		final var calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		calendar.setTimeInMillis(consensusTime.getEpochSecond() * 1_000);
		final int curHour = calendar.get(HOUR_OF_DAY);
		final int curMin = calendar.get(MINUTE);
		final int curMinsSinceMidnight = curHour * 60 + curMin;
		final int nominalMinsSinceMidnight = nominalHour * 60 + nominalMin;
		int diffMins = nominalMinsSinceMidnight - curMinsSinceMidnight;
		if (diffMins < 0) {
			diffMins += 1440;
		}
		return now.plusSeconds(diffMins * 60);
	}

	@Test
	void invalidFreezeTxBody() throws Exception {
		willThrow(IllegalArgumentException.class).given(dualState).setFreezeTime(any());
		final var transaction = createFreezeTransaction(true, false, null);
		final var txBody = CommonUtils.extractTransactionBody(transaction);

		final var record = subject.freeze(txBody, consensusTime);

		assertEquals(INVALID_FREEZE_TRANSACTION_BODY, record.getReceipt().getStatus());
	}

	@Test
	void freezeWithUpdateFeature() throws Exception {
		final String zipFile = "src/test/resources/testfiles/updateFeature/update.zip";
		final var data = Files.readAllBytes(Paths.get(zipFile));
		final var hash = CommonUtils.noThrowSha384HashOf(data);
		final var fileID = FileID.newBuilder().setShardNum(0L).setRealmNum(0L).setFileNum(150L).build();
		final var transaction = createFreezeTransaction(true, true, fileID, hash);
		given(hfs.exists(fileID)).willReturn(true);
		given(hfs.cat(fileID)).willReturn(data);

		final var txBody = CommonUtils.extractTransactionBody(transaction);
		final var record = subject.freeze(txBody, consensusTime);
		assertEquals(SUCCESS, record.getReceipt().getStatus());

		subject.handleUpdateFeature();

		// Wait script to finish
		sleep(2000);

		//check whether new file has been added as expected
		final var file3 = new File("new3.txt");
		assertTrue(file3.exists());
		file3.delete();
	}

	@Test
	void freezeOnlyNoUpdateFeature() throws Exception {
		final var transaction = createFreezeTransaction(true, true, null);
		final var txBody = CommonUtils.extractTransactionBody(transaction);
		final var record = subject.freeze(txBody, consensusTime);
		assertEquals(SUCCESS, record.getReceipt().getStatus());

		subject.handleUpdateFeature();

		assertThat(
				logCaptor.infoLogs(),
				contains(
						Matchers.startsWith("Dual state freeze time set to"),
						stringContainsInOrder(List.of("Update file id is not defined"))));
	}

	@Test
	void freezeUpdateWarnsWhenFileNotDeleted() throws Exception {
		final var zipFile = "src/test/resources/testfiles/updateFeature/update.zip";
		final var data = Files.readAllBytes(Paths.get(zipFile));
		final var hash = CommonUtils.noThrowSha384HashOf(data);
		final var fileID = FileID.newBuilder().setShardNum(0L).setRealmNum(0L).setFileNum(150L).build();
		final var transaction = createFreezeTransaction(true, true, fileID, hash);
		given(hfs.exists(fileID)).willReturn(true);
		given(hfs.cat(fileID)).willReturn(data);

		final var txBody = CommonUtils.extractTransactionBody(transaction);
		final var record = subject.freeze(txBody, consensusTime);
		assertEquals(SUCCESS, record.getReceipt().getStatus());

		try (final var utilities = Mockito.mockStatic(Files.class)) {
			utilities.when(() -> Files.walk(any())).thenReturn(Stream.empty());
			utilities.when(() -> Files.delete(any())).thenThrow(new IOException());

			subject.handleUpdateFeature();
		}

		assertTrue(
				logCaptor.warnLogs().get(1).contains("File could not be deleted"));
	}

	@Test
	void freezeUpdateAbortsOnEmptyFile() throws Exception {
		final var data = new byte[0];
		final var hash = CommonUtils.noThrowSha384HashOf(data);
		final var fileID = FileID.newBuilder().setShardNum(0L).setRealmNum(0L).setFileNum(150L).build();
		final var transaction = createFreezeTransaction(true, true, fileID, hash);
		given(hfs.exists(fileID)).willReturn(true);
		given(hfs.cat(fileID)).willReturn(data);

		final var txBody = CommonUtils.extractTransactionBody(transaction);
		final var record = subject.freeze(txBody, consensusTime);
		assertEquals(SUCCESS, record.getReceipt().getStatus());

		subject.handleUpdateFeature();

		assertThat(
				logCaptor.errorLogs(),
				contains(
						Matchers.equalTo("NETWORK_UPDATE Node 1 Update file is empty"),
						Matchers.equalTo("NETWORK_UPDATE Node 1 ABORT UPDATE PROCRESS")));
	}

	@Test
	void freezeUpdateAbortsOnFileHashMisMatch() throws Exception {
		final var fileID = FileID.newBuilder().setShardNum(0L).setRealmNum(0L).setFileNum(150L).build();
		final var transaction = createFreezeTransaction(true, true, fileID, new byte[48]);
		given(hfs.exists(fileID)).willReturn(true);
		given(hfs.cat(fileID)).willReturn(new byte[100]);

		final var txBody = CommonUtils.extractTransactionBody(transaction);
		final var record = subject.freeze(txBody, consensusTime);
		assertEquals(SUCCESS, record.getReceipt().getStatus());

		subject.handleUpdateFeature();

		assertThat(
				logCaptor.errorLogs(),
				contains(
						stringContainsInOrder(List.of("File hash mismatch")),
						Matchers.startsWith("NETWORK_UPDATE Node 1 Hash from transaction body"),
						Matchers.startsWith("NETWORK_UPDATE Node 1 Hash from file system"),
						Matchers.equalTo("NETWORK_UPDATE Node 1 ABORT UPDATE PROCRESS")));
	}


	@Test
	void freezeUpdateAbortsOnNonExistingFileId() throws Exception {
		final var fileID = FileID.newBuilder().setShardNum(0L).setRealmNum(0L).setFileNum(150L).build();
		final var transaction = createFreezeTransaction(true, true, fileID, new byte[48]);
		given(hfs.exists(fileID)).willReturn(false);
		given(hfs.cat(fileID)).willReturn(new byte[100]);

		final var txBody = CommonUtils.extractTransactionBody(transaction);
		final var record = subject.freeze(txBody, consensusTime);
		assertEquals(SUCCESS, record.getReceipt().getStatus());

		subject.handleUpdateFeature();

		assertThat(
				logCaptor.errorLogs(),
				contains(stringContainsInOrder(List.of("not found in file system"))));
	}
}
