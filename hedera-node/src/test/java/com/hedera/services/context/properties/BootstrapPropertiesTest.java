package com.hedera.services.context.properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import static com.hedera.services.context.properties.BootstrapProperties.BOOTSTRAP_PROP_DEFAULTS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(JUnitPlatform.class)
class BootstrapPropertiesTest {
	BootstrapProperties subject = new BootstrapProperties();

	private String SAMPLE_PROPS_FILE = "src/test/resources/bootstrap/sample.properties";
	private String SAMPLE_NEW_PROPS_FILE = "src/test/resources/bootstrap/sample-new.properties";
	private String INVALID_PROPS_FILE = "src/test/resources/bootstrap/not.properties";
	private String UNREADABLE_PROPS_FILE = "src/test/resources/bootstrap/unreadable.properties";
	private String MISSING_PROPS_FILE = "src/test/resources/bootstrap/missing.properties";

	private String LEGACY_PROPS_FILE = "src/test/resources/bootstrap/legacy.properties";

	@BeforeEach
	public void setup() {
		subject.LEGACY_PROPS_LOC = MISSING_PROPS_FILE;
	}

	@Test
	public void allPropsHaveDefaults() {
		// expect:
		for (String name : BootstrapProperties.BOOTSTRAP_PROP_NAMES) {
			assertTrue(
					BOOTSTRAP_PROP_DEFAULTS.containsKey(name),
					String.format("No default for '%s'!", name));
		}
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
			assertEquals(BOOTSTRAP_PROP_DEFAULTS.get(prop), subject.getLongProperty(prop));
		}
		for (String prop : STRING_PROPS) {
			assertEquals(BOOTSTRAP_PROP_DEFAULTS.get(prop), subject.getStringProperty(prop));
		}
		for (String prop : INTEGER_PROPS) {
			assertEquals(BOOTSTRAP_PROP_DEFAULTS.get(prop), subject.getIntProperty(prop));
		}
	}

	@Test
	public void ignoresProblematicLegacyProps() {
		// setup:
		subject.LEGACY_PROPS_LOC = LEGACY_PROPS_FILE;
		subject.BOOTSTRAP_PROPS_LOC = MISSING_PROPS_FILE;
		// and:
		BootstrapProperties.streamProvider = ignore -> {
			throw new IOException("Oops!");
		};

		// expect:
		assertDoesNotThrow(subject::ensureFileProps);

		// then:
		assertOnlyDefaults();

		// cleanup:
		BootstrapProperties.streamProvider = Files::newInputStream;
	}

	@Test
	public void usesLegacyPropsForDefaultIfPresent() {
		// setup:
		subject.LEGACY_PROPS_LOC = LEGACY_PROPS_FILE;
		subject.BOOTSTRAP_PROPS_LOC = SAMPLE_NEW_PROPS_FILE;

		// when:
		subject.ensureFileProps();

		// then:
		assertEquals(
				BOOTSTRAP_PROP_DEFAULTS.get("bootstrap.genesisB64Keystore.keyName"),
				subject.getStringProperty("bootstrap.genesisB64Keystore.keyName"));
		assertEquals(
				"src/test/resources/bootstrap/b64GenesisKeyPair.txt",
				subject.getStringProperty("bootstrap.genesisB64Keystore.path"));
		assertEquals(
				"src/test/resources/bootstrap/genesis.pem",
				subject.getStringProperty("bootstrap.genesisPem.path"));
		assertEquals(
				"src/test/resources/bootstrap/genesis-passphrase.txt",
				subject.getStringProperty("bootstrap.genesisPemPassphrase.path"));
		assertEquals(
				"FeeSchedule.json",
				subject.getStringProperty("bootstrap.feeSchedulesJson.resource"));
		assertEquals(
				"src/test/resources/bootstrap/api-permission.properties",
				subject.getStringProperty("bootstrap.permissions.path"));
		assertEquals(
				"src/test/resources/bootstrap/application.properties",
				subject.getStringProperty("bootstrap.properties.path"));
		assertEquals(
				2,
				subject.getIntProperty("bootstrap.rates.currentHbarEquiv"));
		assertEquals(
				25,
				subject.getIntProperty("bootstrap.rates.currentCentEquiv"));
		assertEquals(
				5000000000L,
				subject.getLongProperty("bootstrap.rates.currentExpiry"));
		assertEquals(
				2,
				subject.getIntProperty("bootstrap.rates.nextHbarEquiv"));
		assertEquals(
				25,
				subject.getIntProperty("bootstrap.rates.nextCentEquiv"));
		assertEquals(
				5000000000L,
				subject.getLongProperty("bootstrap.rates.nextExpiry"));
		assertEquals(
				5000000000L,
				subject.getLongProperty("bootstrap.systemFilesExpiry"));
	}

	@Test
	public void ensuresFilePropsFromExtant() {
		// setup:
		var expectedProps = new HashSet<>(BootstrapProperties.BOOTSTRAP_PROP_NAMES);

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
				BOOTSTRAP_PROP_DEFAULTS.get("bootstrap.genesisB64Keystore.keyName"),
				subject.getStringProperty("bootstrap.genesisB64Keystore.keyName")
		);
		assertEquals(
				BOOTSTRAP_PROP_DEFAULTS.get("bootstrap.genesisB64Keystore.path"),
				subject.getStringProperty("bootstrap.genesisB64Keystore.path"));
		assertEquals(
				"src/test/resources/bootstrap/genesis.pem",
				subject.getStringProperty("bootstrap.genesisPem.path"));
		assertEquals(
				"src/test/resources/bootstrap/genesis-passphrase.txt",
				subject.getStringProperty("bootstrap.genesisPemPassphrase.path"));
		assertEquals(
				"FeeSchedule.json",
				subject.getStringProperty("bootstrap.feeSchedulesJson.resource"));
		assertEquals(
				"src/test/resources/bootstrap/api-permission.properties",
				subject.getStringProperty("bootstrap.permissions.path"));
		assertEquals(
				"src/test/resources/bootstrap/application.properties",
				subject.getStringProperty("bootstrap.properties.path"));
		assertEquals(
				1,
				subject.getIntProperty("bootstrap.rates.currentHbarEquiv"));
		assertEquals(
				12,
				subject.getIntProperty("bootstrap.rates.currentCentEquiv"));
		assertEquals(
				2000000000L,
				subject.getLongProperty("bootstrap.rates.currentExpiry"));
		assertEquals(
				1,
				subject.getIntProperty("bootstrap.rates.nextHbarEquiv"));
		assertEquals(
				14,
				subject.getIntProperty("bootstrap.rates.nextCentEquiv"));
		assertEquals(
				3000000000L,
				subject.getLongProperty("bootstrap.rates.nextExpiry"));
		assertEquals(
				4000000000L,
				subject.getLongProperty("bootstrap.systemFilesExpiry"));
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
