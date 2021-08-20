package com.hedera.services;

import com.hedera.services.context.properties.PropertiesModule;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.sigs.SigsModule;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.swirlds.common.Platform;
import dagger.BindsInstance;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
		SigsModule.class,
		PropertiesModule.class,
})
public interface ServicesApp {
	SyncVerifier syncVerifier();
	NodeLocalProperties nodeLocalProperties();
	GlobalDynamicProperties globalDynamicProperties();

	@Component.Builder
	interface Builder {
		@BindsInstance
		Builder platform(Platform platform);

		ServicesApp build();
	}
}
