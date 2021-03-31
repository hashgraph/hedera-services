package com.hedera.services.grpc;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.context.properties.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLException;
import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class ConfigDrivenNettyFactoryTest {

	int port = 50123;
	long keepAliveTime = 10;
	long keepAliveTimeout = 3;
	long maxConnectionAge = 15;
	long maxConnectionAgeGrace = 5;
	long maxConnectionIdle = 10;
	int maxConcurrentCalls = 10;

	@Mock
	NodeLocalProperties nodeLocalProperties;

	ConfigDrivenNettyFactory subject;

	@BeforeEach
	void setup() {
		subject = new ConfigDrivenNettyFactory(nodeLocalProperties);
	}

	@Test
	void usesProdPropertiesWhenAppropros() throws FileNotFoundException, SSLException {
		given(nodeLocalProperties.activeProfile()).willReturn(Profile.PROD);
		given(nodeLocalProperties.nettyProdKeepAliveTime()).willReturn(keepAliveTime);
		given(nodeLocalProperties.nettyProdKeepAliveTimeout()).willReturn(keepAliveTimeout);
		given(nodeLocalProperties.nettyMaxConnectionAge()).willReturn(maxConnectionAge);
		given(nodeLocalProperties.nettyMaxConnectionAgeGrace()).willReturn(maxConnectionAgeGrace);
		given(nodeLocalProperties.nettyMaxConnectionIdle()).willReturn(maxConnectionIdle);
		given(nodeLocalProperties.nettyMaxConcurrentCalls()).willReturn(maxConcurrentCalls);

		// when:
		subject.builderFor(port, false).build();

		// then:
		verify(nodeLocalProperties).nettyProdKeepAliveTime();
		verify(nodeLocalProperties).nettyProdKeepAliveTimeout();
		verify(nodeLocalProperties).nettyMaxConnectionAge();
		verify(nodeLocalProperties).nettyMaxConnectionAgeGrace();
		verify(nodeLocalProperties).nettyMaxConnectionIdle();
		verify(nodeLocalProperties).nettyMaxConcurrentCalls();
	}

	@Test
	void failsFastWhenCrtIsMissing() {
		given(nodeLocalProperties.activeProfile()).willReturn(Profile.DEV);
		given(nodeLocalProperties.nettyTlsCrtPath()).willReturn("not-a-real-crt");

		// when:
		assertThrows(FileNotFoundException.class, () -> subject.builderFor(port, true));
	}

	@Test
	void failsFastWhenKeyIsMissing() {
		given(nodeLocalProperties.activeProfile()).willReturn(Profile.DEV);
		given(nodeLocalProperties.nettyTlsCrtPath()).willReturn("src/test/resources/test-hedera.crt");
		given(nodeLocalProperties.nettyTlsKeyPath()).willReturn("not-a-real-key");

		// when:
		assertThrows(FileNotFoundException.class, () -> subject.builderFor(port, true));
	}

	@Test
	void usesSslPropertiesWhenAppropros() throws FileNotFoundException, SSLException {
		given(nodeLocalProperties.activeProfile()).willReturn(Profile.DEV);
		given(nodeLocalProperties.nettyTlsCrtPath()).willReturn("src/test/resources/test-hedera.crt");
		given(nodeLocalProperties.nettyTlsKeyPath()).willReturn("src/test/resources/test-hedera.key");

		// when:
		subject.builderFor(port, true).build();

		// then:
		verify(nodeLocalProperties).nettyTlsCrtPath();
		verify(nodeLocalProperties).nettyTlsKeyPath();
	}
}