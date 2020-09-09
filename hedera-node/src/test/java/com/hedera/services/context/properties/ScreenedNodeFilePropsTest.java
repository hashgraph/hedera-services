package com.hedera.services.context.properties;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class ScreenedNodeFilePropsTest {
	Logger log;

	ScreenedNodeFileProps subject;

	private String STD_NODE_PROPS_LOC = "src/test/resources/bootstrap/node.properties";
	private String LEGACY_NODE_PROPS_LOC = "src/test/resources/bootstrap/legacy-node.properties";

	private static final Map<String, Object> expectedProps = Map.ofEntries(
			entry("grpc.port", 60211),
			entry("grpc.tlsPort", 40212)
	);

	@BeforeEach
	public void setup() {
		log = mock(Logger.class);
		ScreenedNodeFileProps.log = log;
		ScreenedNodeFileProps.NODE_PROPS_LOC = STD_NODE_PROPS_LOC;
		ScreenedNodeFileProps.LEGACY_NODE_PROPS_LOC = LEGACY_NODE_PROPS_LOC;

		subject = new ScreenedNodeFileProps();
	}

	@Test
	public void warnsOfUnparseableAndDeprecated() {
		// expect:
		verify(log).warn(String.format(
				ScreenedNodeFileProps.DEPRECATED_PROP_TPL,
				"tlsPort",
				"grpc.tlsPort",
				STD_NODE_PROPS_LOC));
		// and:
		verify(log).warn(String.format(
				ScreenedNodeFileProps.UNPARSEABLE_PROP_TPL,
				"ABCDEF",
				"grpc.tlsPort",
				"NumberFormatException"));
	}

	@Test
	public void ignoresNonNodeProps() {
		// expect:
		verify(log).warn(String.format(ScreenedNodeFileProps.MISPLACED_PROP_TPL, "hedera.shard"));
	}

	@Test
	public void hasExpectedProps() {
		// expect:
		for (String name : expectedProps.keySet()) {
			assertTrue(subject.containsProperty(name), "Should have '" + name + "'!");
			assertEquals(expectedProps.get(name), subject.getProperty(name));
		}
		// and:
		assertEquals(expectedProps, subject.fromFile);
		// and:
		assertEquals(expectedProps.keySet(), subject.allPropertyNames());
	}
}