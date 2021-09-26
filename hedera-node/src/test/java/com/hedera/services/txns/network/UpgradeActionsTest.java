package com.hedera.services.txns.network;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.platform.state.DualStateImpl;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

import static com.hedera.services.txns.network.UpgradeActions.FREEZE_ABORTED_MARKER;
import static com.hedera.services.txns.network.UpgradeActions.FREEZE_SCHEDULED_MARKER;
import static com.hedera.services.txns.network.UpgradeActions.NOW_FROZEN_MARKER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith({ MockitoExtension.class, LogCaptureExtension.class })
class UpgradeActionsTest {
	private static final Instant then = Instant.ofEpochSecond(1_234_567L, 890);
	private static final String markerFilesLoc = "src/test/resources/upgrade";
	private static final String nonexistentMarkerFilesLoc = "src/test/resources/edargpu";

	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private DualStateImpl dualState;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private UpgradeActions subject;

	@BeforeEach
	void setUp() {
		subject = new UpgradeActions(dynamicProperties, () -> dualState);
	}

	@Test
	void externalizesFreeze() throws IOException {
		given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

		subject.externalizeFreeze();

		assertMarkerCreated(NOW_FROZEN_MARKER);
	}

	@Test
	void abortsScheduledFreeze() throws IOException {
		given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

		subject.scheduleFreezeAt(then);

		verify(dualState).setFreezeTime(then);

		assertMarkerCreated(FREEZE_SCHEDULED_MARKER);
	}

	@Test
	void schedulesFreeze() throws IOException {
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

		given(dualState.getFreezeTime()).willReturn(Instant.EPOCH);

		assertFalse(subject.isFreezeScheduled());

		given(dualState.getFreezeTime()).willReturn(then);

		assertTrue(subject.isFreezeScheduled());
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