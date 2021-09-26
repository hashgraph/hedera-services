package com.hedera.services.txns.network;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.swirlds.common.SwirldDualState;
import com.swirlds.platform.state.DualStateImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Singleton
public class UpgradeActions {
	private static final Logger log = LogManager.getLogger(UpgradeActions.class);

	public static final String NOW_FROZEN_MARKER = "now_frozen.mf";
	public static final String EXEC_IMMEDIATE_MARKER = "execute_immediate.mf";
	public static final String FREEZE_SCHEDULED_MARKER = "freeze_scheduled.mf";
	public static final String FREEZE_ABORTED_MARKER = "freeze_aborted.mf";

	public static final String MARK = "✓";

	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<SwirldDualState> dualState;

	@Inject
	public UpgradeActions(
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<SwirldDualState> dualState
	) {
		this.dualState = dualState;
		this.dynamicProperties = dynamicProperties;
	}

	public void prepareUpgradeNow(byte[] archiveData) {
		throw new AssertionError("Not implemented");
	}

	public void scheduleFreezeAt(final Instant freezeTime) {
		withNonNullDualState("schedule freeze", ds -> {
			ds.setFreezeTime(freezeTime);
			writeMarker(FREEZE_SCHEDULED_MARKER);
		});
	}

	public void abortScheduledFreeze() {
		withNonNullDualState("abort freeze", ds -> {
			ds.setFreezeTime(null);
			writeMarker(FREEZE_ABORTED_MARKER);
		});
	}

	public void externalizeFreeze() {
		writeMarker(NOW_FROZEN_MARKER);
	}

	public boolean isFreezeScheduled() {
		final var ans = new AtomicBoolean();
		withNonNullDualState("check freeze schedule", ds -> {
			final var freezeTime = ((DualStateImpl) ds).getFreezeTime();
			ans.set(freezeTime != null && !Instant.EPOCH.equals(freezeTime));
		});
		return ans.get();
	}

	private void withNonNullDualState(String actionDesc, Consumer<SwirldDualState> action) {
		final var curDualState = dualState.get();
		Objects.requireNonNull(curDualState, "Cannot " + actionDesc + " without access to the dual state");
		action.accept(curDualState);
	}

	private void writeMarker(String file) {
		final var path = Paths.get(dynamicProperties.upgradeArtifactsLoc(), file);
		try {
			Files.writeString(path, MARK);
			log.info("Wrote marker {}", path);
		} catch (IOException e) {
			log.error("Failed to write NMT marker {}", path, e);
			log.error("Manual remediation may be necessary to avoid node ISS");
		}
	}
}
