package com.hedera.services.context.properties;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

@Module
public abstract class CompositePropertiesModule {
	@Provides
	public static PropertySource providePropertySource(PropertySources propertySources) {
		return propertySources.asResolvingSource();
	}

	@Binds
	public abstract PropertySources bindPropertySources(StandardizedPropertySources standardizedPropertySources);
}
