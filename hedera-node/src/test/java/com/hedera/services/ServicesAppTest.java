package com.hedera.services;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.swirlds.common.Platform;
import com.swirlds.common.crypto.Cryptography;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ServicesAppTest {
	private ServicesApp subject;

	@Mock
	private Platform platform;
	@Mock
	private Cryptography cryptography;

	@BeforeEach
	void setUp() {
		given(platform.getCryptography()).willReturn(cryptography);

		subject = DaggerServicesApp.builder()
				.platform(platform)
				.build();
	}

	@Test
	void objectGraphRootsAreAvailable() {
		// expect:
		assertThat(subject.syncVerifier(), instanceOf(SyncVerifier.class));
		assertThat(subject.nodeLocalProperties(), instanceOf(NodeLocalProperties.class));
		assertThat(subject.globalDynamicProperties(), instanceOf(GlobalDynamicProperties.class));
	}
}