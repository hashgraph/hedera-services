package com.hedera.services.context.properties;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.meta.GetVersionInfoAnswer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SemanticVersionsTest {
	SemanticVersion expectedVersions = SemanticVersion.newBuilder()
			.setMajor(0)
			.setMinor(4)
			.setPatch(0)
			.build();
	SemanticVersions subject;

	@BeforeEach
	private void setup() throws Throwable {
		SemanticVersions.VERSION_INFO_RESOURCE = "frozenVersion.properties";

		subject = new SemanticVersions();

		SemanticVersions.knownActive.set(null);
	}

	@Test
	public void recognizesAvailableResource() {
		// when:
		var versions = subject.getDeployed();

		// then:
		assertEquals(expectedVersions, versions.get().protoSemVer());
		assertEquals(expectedVersions, versions.get().hederaSemVer());
	}

	@Test
	public void recognizesUnavailableResource() {
		// setup:
		SemanticVersions.VERSION_INFO_RESOURCE = "nonsense.properties";

		// then:
		assertTrue(subject.getDeployed().isEmpty());

		// cleanup:
		SemanticVersions.VERSION_INFO_RESOURCE = "frozenVersion.properties";
	}
}