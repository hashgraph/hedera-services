package com.hedera.services.txns.network;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.swirlds.platform.state.DualStateImpl;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

import static com.hedera.services.txns.network.UpgradeActions.EXEC_IMMEDIATE_MARKER;
import static com.hedera.services.txns.network.UpgradeActions.FREEZE_ABORTED_MARKER;
import static com.hedera.services.txns.network.UpgradeActions.FREEZE_SCHEDULED_MARKER;
import static com.hedera.services.txns.network.UpgradeActions.NOW_FROZEN_MARKER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith({ MockitoExtension.class, LogCaptureExtension.class })
class UpgradeActionsTest {
	private static final Instant then = Instant.ofEpochSecond(1_234_567L, 890);
	private static final String markerFilesLoc = "src/test/resources/upgrade";
	private static final String nonexistentMarkerFilesLoc = "src/test/resources/edargpu";
	private static final byte[] PRETEND_ARCHIVE =
			"This is missing something. Hard to put a finger on what...".getBytes(StandardCharsets.UTF_8);

	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private DualStateImpl dualState;
	@Mock
	private UpgradeActions.UnzipAction unzipAction;
	@Mock
	private MerkleSpecialFiles specialFiles;
	@Mock
	private MerkleNetworkContext networkCtx;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private UpgradeActions subject;

	@BeforeEach
	void setUp() {
		subject = new UpgradeActions(
				unzipAction, dynamicProperties, () -> dualState, () -> specialFiles, () -> networkCtx);
	}

	@Test
	void complainsLoudlyIfUpgradeHashDoesntMatch() {
		rmIfPresent(EXEC_IMMEDIATE_MARKER);

		given(networkCtx.hasPreparedUpgrade()).willReturn(true);
		given(networkCtx.getPreparedUpdateFileNum()).willReturn(150L);

		subject.catchUpOnMissedSideEffects();

		assertThat(
				logCaptor.errorLogs(),
				contains(
						Matchers.startsWith("Cannot redo NMT upgrade prep, file 0.0.150 changed since FREEZE_UPGRADE"),
						Matchers.equalTo("Manual remediation may be necessary to avoid node ISS")));
		assertFalse(
				Paths.get(markerFilesLoc, EXEC_IMMEDIATE_MARKER).toFile().exists(),
				"Should not create " + EXEC_IMMEDIATE_MARKER + " if prepared file hash doesn't match");
	}

	@Test
	void catchesUpOnUpgradePreparationIfInContext() throws IOException {
		rmIfPresent(EXEC_IMMEDIATE_MARKER);

		given(networkCtx.hasPreparedUpgrade()).willReturn(true);
		given(networkCtx.isPreparedFileHashValidGiven(specialFiles)).willReturn(true);
		given(networkCtx.getPreparedUpdateFileNum()).willReturn(150L);
		given(specialFiles.get(IdUtils.asFile("0.0.150"))).willReturn(PRETEND_ARCHIVE);
		given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

		subject.catchUpOnMissedSideEffects();

		verify(unzipAction).unzip(PRETEND_ARCHIVE, markerFilesLoc);
		assertMarkerCreated(EXEC_IMMEDIATE_MARKER);
	}

	@Test
	void catchUpIsNoopWithNothingToDo() {
		rmIfPresent(FREEZE_SCHEDULED_MARKER);
		rmIfPresent(EXEC_IMMEDIATE_MARKER);

		subject.catchUpOnMissedSideEffects();

		assertFalse(
				Paths.get(markerFilesLoc, EXEC_IMMEDIATE_MARKER).toFile().exists(),
				"Should not create " + EXEC_IMMEDIATE_MARKER + " if no prepared upgrade in state");
		assertFalse(
				Paths.get(markerFilesLoc, FREEZE_SCHEDULED_MARKER).toFile().exists(),
				"Should not create " + FREEZE_SCHEDULED_MARKER + " if dual freeze time is null");
	}

	@Test
	void catchesUpOnFreezeScheduleIfInDual() throws IOException {
		rmIfPresent(FREEZE_SCHEDULED_MARKER);

		given(dualState.getFreezeTime()).willReturn(then);
		given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

		subject.catchUpOnMissedSideEffects();

		assertMarkerCreated(FREEZE_SCHEDULED_MARKER);
	}

	@Test
	void complainsLoudlyWhenUnableToUnzipArchive() throws IOException {
		rmIfPresent(EXEC_IMMEDIATE_MARKER);

		given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);
		willThrow(IOException.class).given(unzipAction).unzip(PRETEND_ARCHIVE, markerFilesLoc);

		subject.prepareUpgradeNow(PRETEND_ARCHIVE);

		assertThat(
				logCaptor.errorLogs(),
				contains(
						Matchers.startsWith("Failed to unzip archive for NMT consumption java.io.IOException: "),
						Matchers.equalTo("Manual remediation may be necessary to avoid node ISS")));
		assertFalse(
				Paths.get(markerFilesLoc, EXEC_IMMEDIATE_MARKER).toFile().exists(),
				"Should not create " + EXEC_IMMEDIATE_MARKER + " if unzip failed");
	}

	@Test
	void preparesForUpgrade() throws IOException {
		rmIfPresent(EXEC_IMMEDIATE_MARKER);

		given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

		subject.prepareUpgradeNow(PRETEND_ARCHIVE);

		verify(unzipAction).unzip(PRETEND_ARCHIVE, markerFilesLoc);
		assertMarkerCreated(EXEC_IMMEDIATE_MARKER);
	}

	@Test
	void externalizesFreeze() throws IOException {
		rmIfPresent(NOW_FROZEN_MARKER);

		given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

		subject.externalizeFreeze();

		assertMarkerCreated(NOW_FROZEN_MARKER);
	}

	@Test
	void abortsScheduledFreeze() throws IOException {
		rmIfPresent(FREEZE_SCHEDULED_MARKER);

		given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

		subject.scheduleFreezeAt(then);

		verify(dualState).setFreezeTime(then);

		assertMarkerCreated(FREEZE_SCHEDULED_MARKER);
	}

	@Test
	void schedulesFreeze() throws IOException {
		rmIfPresent(FREEZE_ABORTED_MARKER);

		given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

		subject.abortScheduledFreeze();

		verify(dualState).setFreezeTime(null);

		assertMarkerCreated(FREEZE_ABORTED_MARKER);
	}

	@Test
	void complainsLoudlyWhenUnableToWriteMarker() {
		given(dynamicProperties.upgradeArtifactsLoc()).willReturn(nonexistentMarkerFilesLoc);
		final var p = Paths.get(nonexistentMarkerFilesLoc, FREEZE_ABORTED_MARKER);

		subject.abortScheduledFreeze();

		verify(dualState).setFreezeTime(null);

		assertThat(
				logCaptor.errorLogs(),
				contains(
						Matchers.startsWith("Failed to write NMT marker " + p),
						Matchers.equalTo("Manual remediation may be necessary to avoid node ISS")));
	}

	@Test
	void determinesIfFreezeIsScheduled() {
		assertFalse(subject.isFreezeScheduled());

		given(dualState.getFreezeTime()).willReturn(then);

		assertTrue(subject.isFreezeScheduled());
	}

	private void rmIfPresent(String file) {
		final var p = Paths.get(markerFilesLoc, file);
		final var f = p.toFile();
		if (f.exists()) {
			f.delete();
		}
	}

	private void assertMarkerCreated(String file) throws IOException {
		final var p = Paths.get(markerFilesLoc, file);
		final var f = p.toFile();
		assertTrue(f.exists(), file + " should have been created, but wasn't");
		final var contents = Files.readString(p);
		assertEquals(UpgradeActions.MARK, contents);
		f.delete();
		assertThat(
				logCaptor.infoLogs(),
				contains(Matchers.equalTo("Wrote marker " + p)));
	}
}