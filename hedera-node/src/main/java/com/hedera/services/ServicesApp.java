package com.hedera.services;

import com.hedera.services.context.properties.CompositePropertiesModule;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = CompositePropertiesModule.class)
public interface ServicesApp {
	NodeLocalProperties nodeLocalProperties();
	GlobalDynamicProperties globalDynamicProperties();
}
