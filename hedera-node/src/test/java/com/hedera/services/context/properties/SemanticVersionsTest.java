package com.hedera.services.context.properties;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(LogCaptureExtension.class)
class SemanticVersionsTest {
	private static final SemanticVersion FROZEN_PROTO_SEMVER = SemanticVersion.newBuilder()
			.setMajor(1)
			.setMinor(2)
			.setPatch(4)
			.setPreReleaseVersion("zeta.123")
			.setBuildMetadata("2b26be40")
			.build();
	private static final SemanticVersion FROZEN_SERVICES_SEMVER = SemanticVersion.newBuilder()
			.setMajor(4)
			.setMinor(2)
			.setPatch(1)
			.setPreReleaseVersion("alpha.0.1.0")
			.setBuildMetadata("04eb62b2")
			.build();

	@Inject
	private LogCaptor logCaptor;

	@LoggingSubject
	private SemanticVersions subject;

	@BeforeEach
	void setUp() {
		subject = new SemanticVersions();
	}

	@Test
	void canParseFullSemver() {
		// given:
		var literal = "1.2.4-alpha.1+2b26be40";
		// and:
		var expected = SemanticVersion.newBuilder()
				.setMajor(1)
				.setMinor(2)
				.setPatch(4)
				.setPreReleaseVersion("alpha.1")
				.setBuildMetadata("2b26be40")
				.build();

		// when:
		var actual = subject.asSemVer(literal);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void canParseWithoutBuildMeta() {
		// given:
		var literal = "1.2.4-alpha.1";
		// and:
		var expected = SemanticVersion.newBuilder()
				.setMajor(1)
				.setMinor(2)
				.setPatch(4)
				.setPreReleaseVersion("alpha.1")
				.build();

		// when:
		var actual = subject.asSemVer(literal);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void canParseWithoutJustReqFields() {
		// given:
		var literal = "1.2.4";
		// and:
		var expected = SemanticVersion.newBuilder()
				.setMajor(1)
				.setMinor(2)
				.setPatch(4)
				.build();

		// when:
		var actual = subject.asSemVer(literal);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void canParseWithoutJustBuildMeta() {
		// given:
		var literal = "1.2.4+2b26be40";
		// and:
		var expected = SemanticVersion.newBuilder()
				.setMajor(1)
				.setMinor(2)
				.setPatch(4)
				.setBuildMetadata("2b26be40")
				.build();

		// when:
		var actual = subject.asSemVer(literal);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void throwsIseWithInvalidLiteral() {
		// given:
		var literal = "1.2..4+2b26be40";

		// expect:
		Assertions.assertThrows(IllegalArgumentException.class, () -> subject.asSemVer(literal));
	}

	@Test
	void recognizesAvailableResource() {
		// setup:
		subject = new SemanticVersions();
		subject.setVersionInfoResource("frozen-semantic-version.properties");

		// when:
		var versions = subject.getDeployed();

		// then:
		assertEquals(FROZEN_PROTO_SEMVER, versions.get().protoSemVer());
		assertEquals(FROZEN_SERVICES_SEMVER, versions.get().hederaSemVer());
	}

	@Test
	void warnsOfUnavailableSemversAndUsesEmpty() {
		// given:
		var shouldBeEmpty = subject.fromResource("nonExistent.properties", "w/e", "n/a");
		// and:
		var desiredPrefix = "Failed to parse resource 'nonExistent.properties' (keys 'w/e' and 'n/a'). " +
				"Version info will be unavailable!";

		// expect:
		assertEquals(SemanticVersion.getDefaultInstance(), shouldBeEmpty.hederaSemVer());
		assertEquals(SemanticVersion.getDefaultInstance(), shouldBeEmpty.protoSemVer());
		assertThat(logCaptor.warnLogs(), contains(Matchers.startsWith(desiredPrefix)));
	}
}
