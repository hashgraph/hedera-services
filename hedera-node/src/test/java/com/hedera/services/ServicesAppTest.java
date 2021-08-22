package com.hedera.services;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.swirlds.common.Platform;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

@ExtendWith(MockitoExtension.class)
class ServicesAppTest {
	private long selfId = 123;
	private ServicesApp subject;

	@Mock
	private Platform platform;
	@Mock
	private ServicesState initialState;

	@BeforeEach
	void setUp() {
		subject = DaggerServicesApp.builder()
				.initialState(initialState)
				.platform(platform)
				.selfId(selfId)
				.build();
	}

	@Test
	void objectGraphRootsAreAvailable() {
		// expect:
		assertThat(subject.nodeLocalProperties(), instanceOf(NodeLocalProperties.class));
		assertThat(subject.globalDynamicProperties(), instanceOf(GlobalDynamicProperties.class));
		// and:
		final var interceptors = subject.interceptors();
		Assertions.assertEquals(2, interceptors.size());
//		interceptors.forEach(System.out::println);
	}
}