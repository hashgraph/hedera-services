package com.hedera.services.context.properties;

import com.hedera.services.context.annotations.BootstrapProps;
import com.hedera.services.context.annotations.CompositeProps;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public abstract class PropertiesModule {
	@Provides
	@Singleton
	@CompositeProps
	public static PropertySource providePropertySource(PropertySources propertySources) {
		return propertySources.asResolvingSource();
	}

	@Binds
	@Singleton
	@BootstrapProps
	public abstract PropertySource bindBootstrapProperties(BootstrapProperties bootstrapProperties);

	@Binds
	@Singleton
	public abstract PropertySources bindPropertySources(StandardizedPropertySources standardizedPropertySources);
}
