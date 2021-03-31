package com.hedera.services.files.sysfiles;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.StandardizedPropertySources;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;

import java.util.function.Consumer;

public class ConfigCallbacks {
	private final GlobalDynamicProperties dynamicProps;
	private final StandardizedPropertySources propertySources;
	private final Consumer<ServicesConfigurationList> legacyPropertiesCb;
	private final Consumer<ServicesConfigurationList> legacyPermissionsCb;

	public ConfigCallbacks(
			GlobalDynamicProperties dynamicProps,
			StandardizedPropertySources propertySources,
			Consumer<ServicesConfigurationList> legacyPropertiesCb,
			Consumer<ServicesConfigurationList> legacyPermissionsCb
	) {
		this.dynamicProps = dynamicProps;
		this.propertySources = propertySources;
		this.legacyPropertiesCb = legacyPropertiesCb;
		this.legacyPermissionsCb = legacyPermissionsCb;
	}

	public Consumer<ServicesConfigurationList> propertiesCb() {
		return config -> {
			propertySources.reloadFrom(config);
			dynamicProps.reload();
			legacyPropertiesCb.accept(config);
		};
	}

	public Consumer<ServicesConfigurationList> permissionsCb() {
		return legacyPermissionsCb::accept;
	}
}
