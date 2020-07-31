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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

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
	Map<String, Object> legacyProps = Collections.emptyMap();

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
			var jutilProps = new Properties();

			try {
				System.out.println(LEGACY_PROPS_LOC);
				var fin = streamProvider.newInputStream(Paths.get(LEGACY_PROPS_LOC));
				jutilProps.load(fin);
				legacyProps = new HashMap<>();
				LEGACY_PROP_NAME_LOOKUP.values()
						.stream()
						.filter(jutilProps.stringPropertyNames()::contains)
						.forEach(legacyProp -> legacyProps.put(
								legacyProp,
								LEGACY_PROP_TRANSFORMS.getOrDefault(legacyProp, s -> s)
										.apply(jutilProps.getProperty(legacyProp))));
			} catch (IOException e) {
				log.warn("Ignoring problem loading legacy props from '{}'.",  LEGACY_PROPS_LOC, e);
			}

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
			var legacyName = LEGACY_PROP_NAME_LOOKUP.getOrDefault(name, "<N/A>");
			if (legacyProps.containsKey(legacyName)) {
				var value = legacyProps.get(legacyName);
				var msg = String.format(
						"Using deprecated property '%s' from '%s' instead of default for '%s'!",
						legacyName,
						LEGACY_PROPS_LOC,
						name);
				log.warn(msg);
				return value;
			} else {
				return BOOTSTRAP_PROP_DEFAULTS.get(name);
			}
		}

	}

	@Override
	public Set<String> allPropertyNames() {
		return BOOTSTRAP_PROP_NAMES;
	}

	public static final Set<String> BOOTSTRAP_PROP_NAMES = unmodifiableSet(of(
			"bootstrap.accounts.addressBookAdmin",
			"bootstrap.accounts.exchangeRatesAdmin",
			"bootstrap.accounts.feeSchedulesAdmin",
			"bootstrap.accounts.freezeAdmin",
			"bootstrap.accounts.init.numSystemAccounts",
			"bootstrap.accounts.systemAdmin",
			"bootstrap.accounts.treasury",
			"bootstrap.files.addressBook",
			"bootstrap.files.dynamicNetworkProps",
			"bootstrap.files.exchangeRates",
			"bootstrap.files.feeSchedules",
			"bootstrap.files.hapiPermissions",
			"bootstrap.files.nodeDetails",
			"bootstrap.feeSchedulesJson.resource",
			"bootstrap.genesisB64Keystore.keyName",
			"bootstrap.genesisB64Keystore.path",
			"bootstrap.genesisPem.path",
			"bootstrap.genesisPemPassphrase.path",
			"bootstrap.hedera.realm",
			"bootstrap.hedera.shard",
			"bootstrap.ledger.nodeAccounts.initialBalance",
			"bootstrap.ledger.systemAccounts.initialBalance",
			"bootstrap.ledger.systemAccounts.recordThresholds",
			"bootstrap.ledger.treasury.initialBalance",
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
			entry("bootstrap.accounts.addressBookAdmin", AS_LONG),
			entry("bootstrap.accounts.exchangeRatesAdmin", AS_LONG),
			entry("bootstrap.accounts.feeSchedulesAdmin", AS_LONG),
			entry("bootstrap.accounts.freezeAdmin", AS_LONG),
			entry("bootstrap.accounts.init.numSystemAccounts", AS_INT),
			entry("bootstrap.accounts.systemAdmin", AS_LONG),
			entry("bootstrap.accounts.treasury", AS_LONG),
			entry("bootstrap.files.addressBook", AS_LONG),
			entry("bootstrap.files.dynamicNetworkProps", AS_LONG),
			entry("bootstrap.files.exchangeRates", AS_LONG),
			entry("bootstrap.files.feeSchedules", AS_LONG),
			entry("bootstrap.files.hapiPermissions", AS_LONG),
			entry("bootstrap.files.nodeDetails", AS_LONG),
			entry("bootstrap.hedera.realm", AS_LONG),
			entry("bootstrap.hedera.shard", AS_LONG),
			entry("bootstrap.ledger.nodeAccounts.initialBalance", AS_LONG),
			entry("bootstrap.ledger.systemAccounts.initialBalance", AS_LONG),
			entry("bootstrap.ledger.systemAccounts.recordThresholds", AS_LONG),
			entry("bootstrap.ledger.treasury.initialBalance", AS_LONG),
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
			entry("bootstrap.ledger.systemAccounts.initialBalance", "initialCoins"),
			entry("bootstrap.ledger.treasury.initialBalance", "initialGenesisCoins"),
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
			entry("bootstrap.accounts.addressBookAdmin", 55L),
			entry("bootstrap.accounts.exchangeRatesAdmin", 57L),
			entry("bootstrap.accounts.feeSchedulesAdmin", 56L),
			entry("bootstrap.accounts.freezeAdmin", 58L),
			entry("bootstrap.accounts.init.numSystemAccounts", 100),
			entry("bootstrap.accounts.systemAdmin", 50L),
			entry("bootstrap.accounts.treasury", 2L),
			entry("bootstrap.files.addressBook", 101L),
			entry("bootstrap.files.dynamicNetworkProps", 121L),
			entry("bootstrap.files.exchangeRates", 112L),
			entry("bootstrap.files.feeSchedules", 111L),
			entry("bootstrap.files.hapiPermissions", 122L),
			entry("bootstrap.files.nodeDetails", 102L),
			entry("bootstrap.feeSchedulesJson.resource", "FeeSchedule.json"),
			entry("bootstrap.genesisB64Keystore.keyName", "START_ACCOUNT"),
			entry("bootstrap.genesisB64Keystore.path", "data/onboard/StartUpAccount.txt"),
			entry("bootstrap.genesisPem.path", "data/onboard/genesis.pem"),
			entry("bootstrap.genesisPemPassphrase.path", "data/onboard/genesis-passphrase.txt"),
			entry("bootstrap.hedera.realm", 0L),
			entry("bootstrap.hedera.shard", 0L),
			entry("bootstrap.ledger.nodeAccounts.initialBalance", 0L),
			entry("bootstrap.ledger.systemAccounts.initialBalance", 0L),
			entry("bootstrap.ledger.systemAccounts.recordThresholds", 5_000_000_000_000_000_000L),
			entry("bootstrap.ledger.treasury.initialBalance", 5_000_000_000_000_000_000L),
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
