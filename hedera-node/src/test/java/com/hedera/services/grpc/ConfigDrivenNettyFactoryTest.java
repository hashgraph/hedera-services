package com.hedera.services.grpc;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.context.properties.Profile;
import io.grpc.netty.NettyServerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLException;
import java.io.FileNotFoundException;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigDrivenNettyFactoryTest {
	int port = 50123;
	long keepAliveTime = 10;

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

		// when:
		subject.builderFor(port, false).build();

		// then:
		verify(nodeLocalProperties).nettyProdKeepAliveTime();
	}
}