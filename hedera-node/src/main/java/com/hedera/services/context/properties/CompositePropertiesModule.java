package com.hedera.services.context.properties;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;

@Module
public abstract class CompositePropertiesModule {
	@Provides @Named("composite")
	public static PropertySource providePropertySource(PropertySources propertySources) {
		return propertySources.asResolvingSource();
	}

	@Binds
	public abstract PropertySources bindPropertySources(StandardizedPropertySources standardizedPropertySources);

	@Binds @Named("bootstrap")
	public abstract PropertySource bindBootstrapProperties(BootstrapProperties bootstrapProperties);
}
