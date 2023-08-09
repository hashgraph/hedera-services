package com.swirlds.platform.startup;

import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.PathsConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.export.ConfigExport;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.internal.ApplicationDefinition;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SystemExitCode;
import com.swirlds.common.system.SystemExitUtils;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.payloads.SavedStateLoadedPayload;
import com.swirlds.platform.JVMPauseDetectorThread;
import com.swirlds.platform.SavedStateLoader;
import com.swirlds.platform.ThreadDumpGenerator;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.config.internal.PlatformConfigUtils;
import com.swirlds.platform.reconnect.emergency.EmergencySignedStateValidator;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.address.AddressBookNetworkUtils;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.swirldapp.AppLoaderException;
import com.swirlds.platform.swirldapp.SwirldAppLoader;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.io.utility.FileUtils.rethrowIO;
import static com.swirlds.common.system.SystemExitUtils.exitSystem;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.state.GenesisStateBuilder.buildGenesisState;
import static com.swirlds.platform.state.signed.ReservedSignedState.createNullReservation;
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;

public final class StartupUtils {
	private static final Logger logger = LogManager.getLogger(StartupUtils.class);

	private StartupUtils() {
	}
	/**
	 * Instantiate and start the thread dump generator.
	 */
	public static void startThreadDumpGenerator(@NonNull final Configuration configuration) {
		final ThreadConfig threadConfig = configuration.getConfigData(ThreadConfig.class);

		if (threadConfig.threadDumpPeriodMs() > 0) {
			final Path dir = getAbsolutePath(threadConfig.threadDumpLogDir());
			if (!Files.exists(dir)) {
				rethrowIO(() -> Files.createDirectories(dir));
			}
			logger.info(STARTUP.getMarker(), "Starting thread dump generator and save to directory {}", dir);
			ThreadDumpGenerator.generateThreadDumpAtIntervals(dir, threadConfig.threadDumpPeriodMs());
		}
	}

	/**
	 * Instantiate and start the JVMPauseDetectorThread, if enabled via the
	 * {@link BasicConfig#jvmPauseDetectorSleepMs()} setting.
	 */
	public static void startJVMPauseDetectorThread(@NonNull final Configuration configuration) {
		final BasicConfig basicConfig = Objects.requireNonNull(configuration).getConfigData(BasicConfig.class);
		if (basicConfig.jvmPauseDetectorSleepMs() > 0) {
			final JVMPauseDetectorThread jvmPauseDetectorThread = new JVMPauseDetectorThread(
					(pauseTimeMs, allocTimeMs) -> {
						if (pauseTimeMs > basicConfig.jvmPauseReportMs()) {
							logger.warn(
									EXCEPTION.getMarker(),
									"jvmPauseDetectorThread detected JVM paused for {} ms, allocation pause {} ms",
									pauseTimeMs,
									allocTimeMs);
						}
					},
					basicConfig.jvmPauseDetectorSleepMs());
			jvmPauseDetectorThread.start();
			logger.debug(STARTUP.getMarker(), "jvmPauseDetectorThread started");
		}
	}

	/**
	 * Build the app main.
	 *
	 * @param appDefinition the app definition
	 * @param appLoader     an object capable of loading the app
	 * @return the new app main
	 */
	public static SwirldMain buildAppMain(final ApplicationDefinition appDefinition, final SwirldAppLoader appLoader) {
		try {
			return appLoader.instantiateSwirldMain();
		} catch (final Exception e) {
			CommonUtils.tellUserConsolePopup(
					"ERROR",
					"ERROR: There are problems starting class " + appDefinition.getMainClassName() + "\n"
							+ ExceptionUtils.getStackTrace(e));
			logger.error(EXCEPTION.getMarker(), "Problems with class {}", appDefinition.getMainClassName(), e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Writes all settings and config values to settingsUsed.txt
	 *
	 * @param configuration the configuration values to write
	 */
	public static void writeSettingsUsed(final Configuration configuration) {
		final StringBuilder settingsUsedBuilder = new StringBuilder();

		// Add all settings values to the string builder
		final PathsConfig pathsConfig = configuration.getConfigData(PathsConfig.class);
		if (Files.exists(pathsConfig.getSettingsPath())) {
			PlatformConfigUtils.generateSettingsUsed(settingsUsedBuilder, configuration);
		}

		settingsUsedBuilder.append(System.lineSeparator());
		settingsUsedBuilder.append("------------- All Configuration -------------");
		settingsUsedBuilder.append(System.lineSeparator());

		// Add all config values to the string builder
		ConfigExport.addConfigContents(configuration, settingsUsedBuilder);

		// Write the settingsUsed.txt file
		final Path settingsUsedPath =
				pathsConfig.getSettingsUsedDir().resolve(PlatformConfigUtils.SETTING_USED_FILENAME);
		try (final OutputStream outputStream = new FileOutputStream(settingsUsedPath.toFile())) {
			outputStream.write(settingsUsedBuilder.toString().getBytes(StandardCharsets.UTF_8));
		} catch (final IOException | RuntimeException e) {
			logger.error(STARTUP.getMarker(), "Failed to write settingsUsed to file {}", settingsUsedPath, e);
		}
	}

	/**
	 * Create a copy of the initial signed state. There are currently data structures that become immutable after
	 * being hashed, and we need to make a copy to force it to become mutable again.
	 *
	 * @param platformContext    the platform's context
	 * @param initialSignedState the initial signed state
	 * @return a copy of the initial signed state
	 */
	public static ReservedSignedState copyInitialSignedState(
			@NonNull final PlatformContext platformContext, @NonNull final SignedState initialSignedState) {

		final State stateCopy = initialSignedState.getState().copy();
		final SignedState signedStateCopy =
				new SignedState(platformContext, stateCopy, "Browser create new copy of initial state");
		signedStateCopy.setSigSet(initialSignedState.getSigSet());

		return signedStateCopy.reserve("Browser copied initial state");
	}

	/**
	 * Update the address book with the current address book read from config.txt. Eventually we will not do this, and
	 * only transactions will be capable of modifying the address book.
	 *
	 * @param signedState the state that was loaded from disk
	 * @param addressBook the address book specified in config.txt
	 */
	public static void updateLoadedStateAddressBook(
			@NonNull final SignedState signedState, @NonNull final AddressBook addressBook) {

		final State state = signedState.getState();

		// Update the address book with the current address book read from config.txt.
		// Eventually we will not do this, and only transactions will be capable of
		// modifying the address book.
		state.getPlatformState().setAddressBook(addressBook.copy());
	}

	/**
	 * Load all {@link SwirldMain} instances for locally run nodes.  Locally run nodes are indicated in two possible
	 * ways.  One is through the set of local nodes to start.  The other is through {@link Address ::isOwnHost} being
	 * true.
	 *
	 * @param appDefinition     the application definition
	 * @param localNodesToStart the locally run nodeIds
	 * @return a map from nodeIds to {@link SwirldMain} instances
	 * @throws AppLoaderException             if there are issues loading the user app
	 * @throws ConstructableRegistryException if there are issues registering
	 *                                        {@link com.swirlds.common.constructable.RuntimeConstructable} classes
	 */
	@NonNull
	public static Map<NodeId, SwirldMain> loadSwirldMains(
			@NonNull final ApplicationDefinition appDefinition, @NonNull final Set<NodeId> localNodesToStart) {
		Objects.requireNonNull(appDefinition, "appDefinition must not be null");
		Objects.requireNonNull(localNodesToStart, "localNodesToStart must not be null");
		try {
			// Create the SwirldAppLoader
			final SwirldAppLoader appLoader;
			try {
				appLoader =
						SwirldAppLoader.loadSwirldApp(appDefinition.getMainClassName(), appDefinition.getAppJarPath());
			} catch (final AppLoaderException e) {
				CommonUtils.tellUserConsolePopup("ERROR", e.getMessage());
				throw e;
			}

			// Register all RuntimeConstructable classes
			logger.debug(STARTUP.getMarker(), "Scanning the classpath for RuntimeConstructable classes");
			final long start = System.currentTimeMillis();
			ConstructableRegistry.getInstance().registerConstructables("", appLoader.getClassLoader());
			logger.debug(
					STARTUP.getMarker(),
					"Done with registerConstructables, time taken {}ms",
					System.currentTimeMillis() - start);

			// Create the SwirldMain instances
			final Map<NodeId, SwirldMain> appMains = new HashMap<>();
			final AddressBook addressBook = appDefinition.getAddressBook();
			for (final Address address : addressBook) {
				if (AddressBookNetworkUtils.isLocal(address)) {
					// if the local nodes to start are not specified, start all local nodes. Otherwise, start specified.
					if (localNodesToStart.isEmpty() || localNodesToStart.contains(address.getNodeId())) {
						appMains.put(address.getNodeId(), buildAppMain(appDefinition, appLoader));
					}
				}
			}
			return appMains;
		} catch (final Exception ex) {
			throw new RuntimeException("Error loading SwirldMains", ex);
		}
	}

	/**
	 * Load the signed state from the disk if it is present.
	 *
	 * @param mainClassName            the name of the app's SwirldMain class.
	 * @param swirldName               the name of the swirld to load the state for.
	 * @param selfId                   the ID of the node to load the state for.
	 * @param appVersion               the version of the app to use for emergency recovery.
	 * @param configAddressBook        the address book to use for emergency recovery.
	 * @param emergencyRecoveryManager the emergency recovery manager to use for emergency recovery.
	 * @return the signed state loaded from disk.
	 */
	@NonNull
	public static ReservedSignedState getUnmodifiedSignedStateFromDisk(
			@NonNull final PlatformContext platformContext,
			@NonNull final String mainClassName,
			@NonNull final String swirldName,
			@NonNull final NodeId selfId,
			@NonNull final SoftwareVersion appVersion,
			@NonNull final AddressBook configAddressBook,
			@NonNull final EmergencyRecoveryManager emergencyRecoveryManager) {

		final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
		final String actualMainClassName = stateConfig.getMainClassName(mainClassName);

		final SavedStateInfo[] savedStateFiles = getSavedStateFiles(actualMainClassName, selfId, swirldName);

		// We can't send a "real" dispatcher for shutdown, since the dispatcher will not have been started by the
		// time this class is used.
		final SavedStateLoader savedStateLoader = new SavedStateLoader(
				platformContext,
				SystemExitUtils::exitSystem,
				configAddressBook,
				savedStateFiles,
				appVersion,
				() -> new EmergencySignedStateValidator(
						stateConfig, emergencyRecoveryManager.getEmergencyRecoveryFile()),
				emergencyRecoveryManager);
		try {
			return savedStateLoader.getSavedStateToLoad();
		} catch (final Exception e) {
			logger.error(EXCEPTION.getMarker(), "Signed state not loaded from disk:", e);
			if (stateConfig.requireStateLoad()) {
				exitSystem(SystemExitCode.SAVED_STATE_NOT_LOADED);
			}
		}
		return createNullReservation();
	}

	/**
	 * Get the initial state to be used by this node. May return a state loaded from disk, or may return a genesis state
	 * if no valid state is found on disk.
	 *
	 * @param platformContext          the platform context
	 * @param appMain                  the app main
	 * @param mainClassName            the name of the app's SwirldMain class
	 * @param swirldName               the name of this swirld
	 * @param selfId                   the node id of this node
	 * @param configAddressBook        the address book from config.txt
	 * @param emergencyRecoveryManager the emergency recovery manager
	 * @return the initial state to be used by this node
	 */
	@NonNull
	public static ReservedSignedState getInitialState(
			@NonNull final PlatformContext platformContext,
			@NonNull final SwirldMain appMain,
			@NonNull final String mainClassName,
			@NonNull final String swirldName,
			@NonNull final NodeId selfId,
			@NonNull final AddressBook configAddressBook,
			@NonNull final EmergencyRecoveryManager emergencyRecoveryManager) {

		Objects.requireNonNull(platformContext);
		Objects.requireNonNull(mainClassName);
		Objects.requireNonNull(swirldName);
		Objects.requireNonNull(selfId);
		Objects.requireNonNull(configAddressBook);
		Objects.requireNonNull(emergencyRecoveryManager);

		final ReservedSignedState loadedState = getUnmodifiedSignedStateFromDisk(
				platformContext,
				mainClassName,
				swirldName,
				selfId,
				appMain.getSoftwareVersion(),
				configAddressBook,
				emergencyRecoveryManager);

		try (loadedState) {
			if (loadedState.isNotNull()) {
				logger.info(
						STARTUP.getMarker(),
						new SavedStateLoadedPayload(
								loadedState.get().getRound(), loadedState.get().getConsensusTimestamp()));

				return copyInitialSignedState(platformContext, loadedState.get());
			}
		}

		final ReservedSignedState genesisState =
				buildGenesisState(platformContext, configAddressBook, appMain.getSoftwareVersion(), appMain.newState());

		try (genesisState) {
			return copyInitialSignedState(platformContext, genesisState.get());
		}
	}
}
