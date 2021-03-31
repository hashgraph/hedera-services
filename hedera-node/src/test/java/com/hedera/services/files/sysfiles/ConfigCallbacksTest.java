package com.hedera.services.files.sysfiles;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.StandardizedPropertySources;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class ConfigCallbacksTest {
	@Mock
	GlobalDynamicProperties dynamicProps;
	@Mock
	StandardizedPropertySources propertySources;
	@Mock
	Consumer<ServicesConfigurationList> legacyPropertiesCb;
	@Mock
	Consumer<ServicesConfigurationList> legacyPermissionsCb;

	ConfigCallbacks subject;

	@BeforeEach
	void setUp() {
		subject = new ConfigCallbacks(dynamicProps, propertySources, legacyPropertiesCb, legacyPermissionsCb);
	}

	@Test
	void propertiesCbAsExpected() {
		var config = ServicesConfigurationList.getDefaultInstance();

		// when:
		subject.propertiesCb().accept(config);

		// then:
		verify(propertySources).reloadFrom(config);
		verify(dynamicProps).reload();
		verify(legacyPropertiesCb).accept(config);
	}

	@Test
	void permissionsCbAsExpected() {
		var config = ServicesConfigurationList.getDefaultInstance();

		// when:
		subject.permissionsCb().accept(config);

		// then:
		verify(legacyPermissionsCb).accept(config);
	}
}
