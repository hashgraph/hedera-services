package com.hedera.services;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.context.properties.PropertiesModule;
import com.hedera.services.fees.FeesModule;
import com.hedera.services.fees.charging.NarratedCharging;
import com.hedera.services.sigs.SigsModule;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.StateModule;
import com.swirlds.common.Platform;
import dagger.BindsInstance;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
		FeesModule.class,
		SigsModule.class,
		StateModule.class,
		PropertiesModule.class,
})
public interface ServicesApp {
	SyncVerifier syncVerifier();
	NarratedCharging narratedCharging();
	NodeLocalProperties nodeLocalProperties();
	GlobalDynamicProperties globalDynamicProperties();

	@Component.Builder
	interface Builder {
		@BindsInstance
		Builder platform(Platform platform);
		@BindsInstance
		Builder selfId(long selfId);
		@BindsInstance
		Builder initialState(ServicesState initialState);

		ServicesApp build();
	}
}
