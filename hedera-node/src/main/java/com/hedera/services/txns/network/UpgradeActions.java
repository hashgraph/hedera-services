package com.hedera.services.txns.network;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
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

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.utils.EntityIdUtils.readableId;

@Singleton
public class UpgradeActions {
	private static final Logger log = LogManager.getLogger(UpgradeActions.class);

	private static final String MANUAL_REMEDIATION_ALERT = "Manual remediation may be necessary to avoid node ISS";

	public static final String NOW_FROZEN_MARKER = "now_frozen.mf";
	public static final String EXEC_IMMEDIATE_MARKER = "execute_immediate.mf";
	public static final String FREEZE_SCHEDULED_MARKER = "freeze_scheduled.mf";
	public static final String FREEZE_ABORTED_MARKER = "freeze_aborted.mf";

	public static final String MARK = "✓";

	public interface UnzipAction {
		void unzip(byte[] archiveData, String artifactsLoc) throws IOException;
	}

	private final UnzipAction unzipAction;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<SwirldDualState> dualState;
	private final Supplier<MerkleSpecialFiles> specialFiles;
	private final Supplier<MerkleNetworkContext> networkCtx;

	@Inject
	public UpgradeActions(
			final UnzipAction unzipAction,
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<SwirldDualState> dualState,
			final Supplier<MerkleSpecialFiles> specialFiles,
			final Supplier<MerkleNetworkContext> networkCtx
	) {
		this.dualState = dualState;
		this.networkCtx = networkCtx;
		this.unzipAction = unzipAction;
		this.specialFiles = specialFiles;
		this.dynamicProperties = dynamicProperties;
	}

	public void externalizeFreeze() {
		writeMarker(NOW_FROZEN_MARKER);
	}

	public void extractNow(byte[] archiveData) {
		try {
			final var artifactsLoc = dynamicProperties.upgradeArtifactsLoc();
			log.info("About to unzip {} bytes into {}", archiveData.length, artifactsLoc);
			unzipAction.unzip(archiveData, artifactsLoc);
			log.info("Finished unzipping {} bytes into {}", archiveData.length, artifactsLoc);
			writeMarker(EXEC_IMMEDIATE_MARKER);
		} catch (IOException e) {
			log.error("Failed to unzip archive for NMT consumption", e);
			log.error(MANUAL_REMEDIATION_ALERT);
		}
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

	public boolean isFreezeScheduled() {
		final var ans = new AtomicBoolean();
		withNonNullDualState("check freeze schedule", ds -> {
			final var freezeTime = ((DualStateImpl) ds).getFreezeTime();
			ans.set(freezeTime != null);
		});
		return ans.get();
	}

	public void catchUpOnMissedSideEffects() {
		catchUpOnMissedUpgradePrep();
		catchUpOnMissedFreezeScheduling();
	}

	private void catchUpOnMissedUpgradePrep() {
		final var curNetworkCtx = networkCtx.get();
		if (!curNetworkCtx.hasPreparedUpgrade()) {
			return;
		}

		final var curSpecialFiles = specialFiles.get();
		final var upgradeFileNum = curNetworkCtx.getPreparedUpdateFileNum();
		final var upgradeFileId = STATIC_PROPERTIES.scopedFileWith(upgradeFileNum);
		if (!curNetworkCtx.isPreparedFileHashValidGiven(curSpecialFiles)) {
			log.error(
					"Cannot redo NMT upgrade prep, file {} changed since FREEZE_UPGRADE",
					readableId(upgradeFileId));
			log.error(MANUAL_REMEDIATION_ALERT);
			return;
		}

		final var archiveData = curSpecialFiles.get(upgradeFileId);
		extractNow(archiveData);
	}

	private void catchUpOnMissedFreezeScheduling() {
		if (isFreezeScheduled()) {
			writeMarker(FREEZE_SCHEDULED_MARKER);
		}
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
			log.error(MANUAL_REMEDIATION_ALERT);
		}
	}
}
