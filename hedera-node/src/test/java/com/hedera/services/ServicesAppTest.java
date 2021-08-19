package com.hedera.services;

import com.hedera.services.context.properties.NodeLocalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

class ServicesAppTest {
	private ServicesApp subject;

	@BeforeEach
	void setUp() {
		subject = DaggerServicesApp.create();
	}

	@Test
	void propertySourcesAreAvailable() {
		// expect:
		assertThat(subject.nodeLocalProperties(), instanceOf(NodeLocalProperties.class));
	}
}