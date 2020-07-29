package com.hedera.services.context.properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableSet;
import static java.util.Set.of;
import static java.util.Map.entry;

public class BootstrapProperties implements PropertySource {
	private static final Properties MISSING_FILE_PROPS = null;

	static Logger log = LogManager.getLogger(BootstrapProperties.class);

	@FunctionalInterface
	interface ThrowingStreamProvider {
		InputStream newInputStream(Path p) throws IOException;
	}
	static ThrowingStreamProvider streamProvider = Files::newInputStream;

	String LEGACY_PROPS_LOC = "data/config/application.properties";
	String BOOTSTRAP_PROPS_LOC = "data/config/bootstrap.properties";

	Properties fileProps = MISSING_FILE_PROPS;
	Map<String, String> legacyProps = Collections.emptyMap();

	void initFileProps() {
		initLegacyProps();

		fileProps = new Properties();

		if (!new File(BOOTSTRAP_PROPS_LOC).exists()) {
			log.warn("'{}' not present, using only defaults!", BOOTSTRAP_PROPS_LOC);
			return;
		}

		load(BOOTSTRAP_PROPS_LOC, fileProps);

		Set<String> unrecognizedProps = new HashSet<>(fileProps.stringPropertyNames());
		unrecognizedProps.removeAll(BOOTSTRAP_PROP_NAMES);
		if (!unrecognizedProps.isEmpty()) {
			var msg = String.format(
					"'%s' contains unrecognized properties: %s!",
					BOOTSTRAP_PROPS_LOC,
					unrecognizedProps);
			throw new IllegalStateException(msg);
		}
	}

	private void initLegacyProps() {
		if (!new File(LEGACY_PROPS_LOC).exists()) {
			return;
		} else {

		}
	}

	private void load(String fromLoc, Properties intoProps) {
		InputStream fin;
		try {
			fin = streamProvider.newInputStream(Paths.get(fromLoc));
		} catch (IOException e) {
			throw new IllegalStateException(
					String.format("'%s' could not be loaded!", fromLoc),
					e);
		}

		try {
			intoProps.load(fin);
		} catch (IOException e) {
			throw new IllegalStateException(
					String.format("'%s' could not be loaded!", fromLoc),
					e);
		}
	}

	void ensureFileProps() {
		if (fileProps == MISSING_FILE_PROPS) {
			initFileProps();
		}
	}

	@Override
	public boolean containsProperty(String name) {
		return BOOTSTRAP_PROP_NAMES.contains(name);
	}

	@Override
	public Object getProperty(String name) {
		ensureFileProps();
		if (fileProps.containsKey(name)) {
			return BOOTSTRAP_PROP_TRANSFORMS.getOrDefault(name, s -> s).apply(fileProps.getProperty(name));
		} else {
			return BOOTSTRAP_PROP_DEFAULTS.get(name);
		}
	}

	@Override
	public Set<String> allPropertyNames() {
		ensureFileProps();
		return fileProps.stringPropertyNames();
	}

	static final Set<String> BOOTSTRAP_PROP_NAMES = unmodifiableSet(of(
			"bootstrap.genesisB64Keystore.keyName",
			"bootstrap.genesisB64Keystore.path",
			"bootstrap.genesisPem.path",
			"bootstrap.genesisPemPassphrase.path",
			"bootstrap.feeSchedulesJson.resource",
			"bootstrap.permissions.path",
			"bootstrap.properties.path",
			"bootstrap.rates.currentHbarEquiv",
			"bootstrap.rates.currentCentEquiv",
			"bootstrap.rates.currentExpiry",
			"bootstrap.rates.nextHbarEquiv",
			"bootstrap.rates.nextCentEquiv",
			"bootstrap.rates.nextExpiry",
			"bootstrap.systemFilesExpiry"
	));
	private static Function<String, Object> AS_INT = Integer::valueOf;
	private static Function<String, Object> AS_LONG = Long::valueOf;
	private static final Map<String, Function<String, Object>> BOOTSTRAP_PROP_TRANSFORMS = Map.ofEntries(
			entry("bootstrap.rates.currentHbarEquiv", AS_INT),
			entry("bootstrap.rates.currentCentEquiv", AS_INT),
			entry("bootstrap.rates.currentExpiry", AS_LONG),
			entry("bootstrap.rates.nextHbarEquiv", AS_INT),
			entry("bootstrap.rates.nextCentEquiv", AS_INT),
			entry("bootstrap.rates.nextExpiry", AS_LONG),
			entry("bootstrap.systemFilesExpiry", AS_LONG)
	);
	private static final Map<String, Function<String, Object>> LEGACY_PROP_TRANSFORMS = Map.ofEntries(
			entry("currentHbarEquivalent", AS_INT),
			entry("currentCentEquivalent", AS_INT),
			entry("expiryTime", AS_LONG)
	);
	private static final Map<String, String> LEGACY_PROP_NAME_LOOKUP = Map.ofEntries(
			entry("bootstrap.genesisB64Keystore.path", "genesisAccountPath"),
			entry("bootstrap.rates.currentHbarEquiv", "currentHbarEquivalent"),
			entry("bootstrap.rates.currentCentEquiv", "currentCentEquivalent"),
			entry("bootstrap.rates.currentExpiry", "expiryTime"),
			entry("bootstrap.rates.nextHbarEquiv", "currentHbarEquivalent"),
			entry("bootstrap.rates.nextCentEquiv", "currentCentEquivalent"),
			entry("bootstrap.rates.nextExpiry", "expiryTime"),
			entry("bootstrap.systemFilesExpiry", "expiryTime")
	);
	private static final long SECS_IN_A_HUNDRED_YEARS = 100 * 365 * 24 * 60 * 60;
	private static final long DEFAULT_EXPIRY = Instant.now().getEpochSecond() + SECS_IN_A_HUNDRED_YEARS;
	static final Map<String, Object> BOOTSTRAP_PROP_DEFAULTS = Map.ofEntries(
			entry("bootstrap.genesisB64Keystore.keyName", "START_ACCOUNT"),
			entry("bootstrap.genesisB64Keystore.path", "data/onboard/StartUpAccount.txt"),
			entry("bootstrap.genesisPem.path", "data/onboard/genesis.pem"),
			entry("bootstrap.genesisPemPassphrase.path", "data/onboard/genesis-passphrase.txt"),
			entry("bootstrap.feeSchedulesJson.resource", "FeeSchedule.json"),
			entry("bootstrap.permissions.path", "data/config/api-permission.properties"),
			entry("bootstrap.properties.path", "data/config/application.properties"),
			entry("bootstrap.rates.currentHbarEquiv", 1),
			entry("bootstrap.rates.currentCentEquiv", 12),
			entry("bootstrap.rates.currentExpiry", DEFAULT_EXPIRY),
			entry("bootstrap.rates.nextHbarEquiv", 1),
			entry("bootstrap.rates.nextCentEquiv", 12),
			entry("bootstrap.rates.nextExpiry", DEFAULT_EXPIRY),
			entry("bootstrap.systemFilesExpiry", DEFAULT_EXPIRY)
	);
}
