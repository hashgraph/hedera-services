package com.hedera.services.context.properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(JUnitPlatform.class)
class BootstrapPropertiesTest {
	BootstrapProperties subject = new BootstrapProperties();

	private String SAMPLE_PROPS_FILE = "src/test/resources/bootstrap/sample.properties";
	private String INVALID_PROPS_FILE = "src/test/resources/bootstrap/not.properties";
	private String UNREADABLE_PROPS_FILE = "src/test/resources/bootstrap/unreadable.properties";
	private String MISSING_PROPS_FILE = "src/test/resources/bootstrap/missing.properties";

	private String LEGACY_PROPS_FILE = "src/test/resources/bootstrap/legacy.properties";

	@BeforeEach
	public void setup() {
		subject.LEGACY_PROPS_LOC = MISSING_PROPS_FILE;
	}

	@Test
	public void throwsIseIfUnreadable() {
		// given:
		subject.BOOTSTRAP_PROPS_LOC = UNREADABLE_PROPS_FILE;

		// expect:
		assertThrows(IllegalStateException.class, subject::ensureFileProps);
	}

	@Test
	public void throwsIseIfIoExceptionOccurs() {
		// setup:
		subject.BOOTSTRAP_PROPS_LOC = SAMPLE_PROPS_FILE;
		// and:
		BootstrapProperties.streamProvider = ignore -> {
			throw new IOException("Oops!");
		};

		// expect:
		assertThrows(IllegalStateException.class, subject::ensureFileProps);

		// cleanup:
		BootstrapProperties.streamProvider = Files::newInputStream;
	}

	@Test
	public void throwsIseIfInvalid() {
		// given:
		subject.BOOTSTRAP_PROPS_LOC = INVALID_PROPS_FILE;

		// expect:
		assertThrows(IllegalStateException.class, subject::ensureFileProps);
	}

	@Test
	public void usesDefaultIfBootstrapPropsAreMissing() {
		// given:
		subject.BOOTSTRAP_PROPS_LOC = MISSING_PROPS_FILE;

		// when:
		subject.ensureFileProps();

		// expect:
		assertOnlyDefaults();
	}

	private void assertOnlyDefaults() {
		for (String prop : LONG_PROPS) {
			assertEquals(BootstrapProperties.BOOTSTRAP_PROP_DEFAULTS.get(prop), subject.getLongProperty(prop));
		}
		for (String prop : STRING_PROPS) {
			assertEquals(BootstrapProperties.BOOTSTRAP_PROP_DEFAULTS.get(prop), subject.getStringProperty(prop));
		}
		for (String prop : INTEGER_PROPS) {
			assertEquals(BootstrapProperties.BOOTSTRAP_PROP_DEFAULTS.get(prop), subject.getIntProperty(prop));
		}
	}

	@Test
	public void usesLegacyOverridesIfPresent() {
		// setup:
		subject.LEGACY_PROPS_LOC = LEGACY_PROPS_FILE;
		subject.BOOTSTRAP_PROPS_LOC = SAMPLE_PROPS_FILE;

		// when:
		subject.ensureFileProps();

		// then:
		assertEquals(
				subject.getStringProperty("bootstrap.genesisB64Keystore.keyName"),
				BootstrapProperties.BOOTSTRAP_PROP_DEFAULTS.get("bootstrap.genesisB64Keystore.keyName"));
		assertEquals(
				subject.getStringProperty("bootstrap.genesisB64Keystore.path"),
				"src/test/resources/bootstrap/b64GenesisKeyPair.txt");
		assertEquals(
				subject.getStringProperty("bootstrap.genesisPem.path"),
				"src/test/resources/bootstrap/genesis.pem");
		assertEquals(
				subject.getStringProperty("bootstrap.genesisPemPassphrase.path"),
				"src/test/resources/bootstrap/genesis-passphrase.txt");
		assertEquals(
				subject.getStringProperty("bootstrap.feeSchedulesJson.resource"),
				"FeeSchedule.json");
		assertEquals(
				subject.getStringProperty("bootstrap.permissions.path"),
				"src/test/resources/bootstrap/api-permission.properties");
		assertEquals(
				subject.getStringProperty("bootstrap.properties.path"),
				"src/test/resources/bootstrap/application.properties");
		assertEquals(
				subject.getIntProperty("bootstrap.rates.currentHbarEquiv"),
				1);
		assertEquals(
				subject.getIntProperty("bootstrap.rates.currentCentEquiv"),
				12);
		assertEquals(
				subject.getLongProperty("bootstrap.rates.currentExpiry"),
				2000000000L);
		assertEquals(
				subject.getIntProperty("bootstrap.rates.nextHbarEquiv"),
				1);
		assertEquals(
				subject.getIntProperty("bootstrap.rates.nextCentEquiv"),
				14);
		assertEquals(
				subject.getLongProperty("bootstrap.rates.nextExpiry"),
				3000000000L);
		assertEquals(
				subject.getLongProperty("bootstrap.systemFilesExpiry"),
				4000000000L);
	}

	@Test
	public void ensuresFilePropsFromExtant() {
		// setup:
		var expectedProps = new HashSet<>(BootstrapProperties.BOOTSTRAP_PROP_NAMES);
		expectedProps.remove("bootstrap.genesisB64Keystore.keyName");
		expectedProps.remove("bootstrap.genesisB64Keystore.path");

		// given:
		subject.BOOTSTRAP_PROPS_LOC = SAMPLE_PROPS_FILE;

		// when:
		subject.ensureFileProps();

		// then:
		assertEquals(expectedProps, subject.allPropertyNames());
		// and:
		for (String prop : BootstrapProperties.BOOTSTRAP_PROP_NAMES) {
			assertTrue(subject.containsProperty(prop), String.format("Doesn't contain expected prop '%s'", prop));
		}
		// and:
		assertEquals(
				subject.getStringProperty("bootstrap.genesisB64Keystore.keyName"),
				BootstrapProperties.BOOTSTRAP_PROP_DEFAULTS.get("bootstrap.genesisB64Keystore.keyName"));
		assertEquals(
				subject.getStringProperty("bootstrap.genesisB64Keystore.path"),
				BootstrapProperties.BOOTSTRAP_PROP_DEFAULTS.get("bootstrap.genesisB64Keystore.path"));
		assertEquals(
				subject.getStringProperty("bootstrap.genesisPem.path"),
				"src/test/resources/bootstrap/genesis.pem");
		assertEquals(
				subject.getStringProperty("bootstrap.genesisPemPassphrase.path"),
				"src/test/resources/bootstrap/genesis-passphrase.txt");
		assertEquals(
				subject.getStringProperty("bootstrap.feeSchedulesJson.resource"),
				"FeeSchedule.json");
		assertEquals(
				subject.getStringProperty("bootstrap.permissions.path"),
				"src/test/resources/bootstrap/api-permission.properties");
		assertEquals(
				subject.getStringProperty("bootstrap.properties.path"),
				"src/test/resources/bootstrap/application.properties");
		assertEquals(
				subject.getIntProperty("bootstrap.rates.currentHbarEquiv"),
				1);
		assertEquals(
				subject.getIntProperty("bootstrap.rates.currentCentEquiv"),
				12);
		assertEquals(
				subject.getLongProperty("bootstrap.rates.currentExpiry"),
				2000000000L);
		assertEquals(
				subject.getIntProperty("bootstrap.rates.nextHbarEquiv"),
				1);
		assertEquals(
				subject.getIntProperty("bootstrap.rates.nextCentEquiv"),
				14);
		assertEquals(
				subject.getLongProperty("bootstrap.rates.nextExpiry"),
				3000000000L);
		assertEquals(
				subject.getLongProperty("bootstrap.systemFilesExpiry"),
				4000000000L);
	}

	private static Set<String> LONG_PROPS = Set.of(
			"bootstrap.rates.currentExpiry",
			"bootstrap.rates.nextExpiry",
			"bootstrap.systemFilesExpiry"
	);
	private static Set<String> STRING_PROPS = Set.of(
			"bootstrap.genesisB64Keystore.keyName",
			"bootstrap.genesisB64Keystore.path",
			"bootstrap.genesisPem.path",
			"bootstrap.genesisPemPassphrase.path",
			"bootstrap.feeSchedulesJson.resource",
			"bootstrap.permissions.path",
			"bootstrap.properties.path"
	);
	private static Set<String> INTEGER_PROPS = Set.of(
			"bootstrap.rates.currentHbarEquiv",
			"bootstrap.rates.currentCentEquiv",
			"bootstrap.rates.nextHbarEquiv",
			"bootstrap.rates.nextCentEquiv"
	);
}
