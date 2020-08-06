package com.hedera.services.context.properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableSet;
import static java.util.Set.of;
import static java.util.Map.entry;
import static java.util.stream.Collectors.toList;

public class BootstrapProperties implements PropertySource {
	private static Map<String, Object> MISSING_PROPS = null;

	private static Function<String, InputStream> nullableResourceStreamProvider =
			BootstrapProperties.class.getClassLoader()::getResourceAsStream;

	static Logger log = LogManager.getLogger(BootstrapProperties.class);

	static ThrowingStreamProvider resourceStreamProvider = resource -> {
		var in = nullableResourceStreamProvider.apply(resource);
		if (in == null) {
			throw new IOException(String.format("Resource '%s' cannot be loaded.", resource));
		}
		return in;
	};

	String BOOTSTRAP_PROPS_RESOURCE = "bootstrap.properties";

	Map<String, Object> bootstrapProps = MISSING_PROPS;

	void initPropsFromResource() {
		var resourceProps = new Properties();
		load(BOOTSTRAP_PROPS_RESOURCE, resourceProps);

		Set<String> unrecognizedProps = new HashSet<>(resourceProps.stringPropertyNames());
		unrecognizedProps.removeAll(BOOTSTRAP_PROP_NAMES);
		if (!unrecognizedProps.isEmpty()) {
			var msg = String.format(
					"'%s' contains unrecognized properties: %s!",
					BOOTSTRAP_PROPS_RESOURCE,
					unrecognizedProps);
			throw new IllegalStateException(msg);
		}
		var missingProps = BOOTSTRAP_PROP_NAMES.stream()
				.filter(name -> !resourceProps.containsKey(name))
				.sorted()
				.collect(toList());
		if (!missingProps.isEmpty()) {
			var msg = String.format(
					"'%s' is missing properties: %s!",
					BOOTSTRAP_PROPS_RESOURCE,
					missingProps);
			throw new IllegalStateException(msg);
		}

		bootstrapProps = new HashMap<>();
		BOOTSTRAP_PROP_NAMES
				.stream()
				.forEach(prop -> bootstrapProps.put(
						prop,
						BOOTSTRAP_PROP_TRANSFORMS.getOrDefault(prop, s -> s)
								.apply(resourceProps.getProperty(prop))));

		var msg = "Resolved bootstrap and global/static properties:\n  " + BOOTSTRAP_PROP_NAMES.stream()
				.sorted()
				.map(name -> String.format("%s=%s", name, bootstrapProps.get(name)))
				.collect(Collectors.joining("\n  "));
		log.info(msg);
	}

	private void load(String resource, Properties intoProps) {
		InputStream fin;
		try {
			fin = resourceStreamProvider.newInputStream(resource);
			intoProps.load(fin);
		} catch (IOException e) {
			throw new IllegalStateException(
					String.format("'%s' could not be loaded!", resource),
					e);
		}
	}

	void ensureProps() {
		if (bootstrapProps == MISSING_PROPS) {
			initPropsFromResource();
		}
	}

	@Override
	public boolean containsProperty(String name) {
		return BOOTSTRAP_PROP_NAMES.contains(name);
	}

	@Override
	public Object getProperty(String name) {
		ensureProps();
		if (bootstrapProps.containsKey(name)) {
			return bootstrapProps.get(name);
		} else {
			throw new IllegalArgumentException(String.format("No such property '%s'!", name));
		}
	}

	@Override
	public Set<String> allPropertyNames() {
		return BOOTSTRAP_PROP_NAMES;
	}

	public static final Set<String> BOOTSTRAP_PROP_NAMES = unmodifiableSet(of(
			"accounts.addressBookAdmin",
			"accounts.exchangeRatesAdmin",
			"accounts.feeSchedulesAdmin",
			"accounts.freezeAdmin",
			"accounts.systemAdmin",
			"accounts.treasury",
			"bootstrap.feeSchedulesJson.resource",
			"bootstrap.genesisB64Keystore.path",
			"bootstrap.genesisB64Keystore.keyName",
			"bootstrap.genesisPem.path",
			"bootstrap.genesisPemPassphrase.path",
			"bootstrap.hapiPermissions.path",
			"bootstrap.ledger.nodeAccounts.initialBalance",
			"bootstrap.ledger.systemAccounts.initialBalance",
			"bootstrap.ledger.systemAccounts.recordThresholds",
			"bootstrap.networkProperties.path",
			"bootstrap.rates.currentHbarEquiv",
			"bootstrap.rates.currentCentEquiv",
			"bootstrap.rates.currentExpiry",
			"bootstrap.rates.nextHbarEquiv",
			"bootstrap.rates.nextCentEquiv",
			"bootstrap.rates.nextExpiry",
			"bootstrap.system.entityExpiry",
			"files.addressBook",
			"files.networkProperties",
			"files.exchangeRates",
			"files.feeSchedules",
			"files.hapiPermissions",
			"files.nodeDetails",
			"hedera.realm",
			"hedera.shard",
			"ledger.numSystemAccounts",
			"ledger.totalTinyBarFloat"
	));

	private static final Map<String, Function<String, Object>> BOOTSTRAP_PROP_TRANSFORMS = Map.ofEntries(
			entry("accounts.addressBookAdmin", AS_LONG),
			entry("accounts.exchangeRatesAdmin", AS_LONG),
			entry("accounts.feeSchedulesAdmin", AS_LONG),
			entry("accounts.freezeAdmin", AS_LONG),
			entry("accounts.systemAdmin", AS_LONG),
			entry("accounts.treasury", AS_LONG),
			entry("files.addressBook", AS_LONG),
			entry("files.networkProperties", AS_LONG),
			entry("files.exchangeRates", AS_LONG),
			entry("files.feeSchedules", AS_LONG),
			entry("files.hapiPermissions", AS_LONG),
			entry("files.nodeDetails", AS_LONG),
			entry("hedera.realm", AS_LONG),
			entry("hedera.shard", AS_LONG),
			entry("bootstrap.ledger.nodeAccounts.initialBalance", AS_LONG),
			entry("bootstrap.ledger.systemAccounts.initialBalance", AS_LONG),
			entry("bootstrap.ledger.systemAccounts.recordThresholds", AS_LONG),
			entry("bootstrap.rates.currentHbarEquiv", AS_INT),
			entry("bootstrap.rates.currentCentEquiv", AS_INT),
			entry("bootstrap.rates.currentExpiry", AS_LONG),
			entry("bootstrap.rates.nextHbarEquiv", AS_INT),
			entry("bootstrap.rates.nextCentEquiv", AS_INT),
			entry("bootstrap.rates.nextExpiry", AS_LONG),
			entry("bootstrap.system.entityExpiry", AS_LONG),
			entry("ledger.numSystemAccounts", AS_INT),
			entry("ledger.totalTinyBarFloat", AS_LONG)
	);
}
