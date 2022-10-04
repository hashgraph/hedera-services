package com.hedera.services.state.tasks;

import com.hedera.services.state.expiry.ExpiryProcess;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;

import javax.inject.Singleton;

@Module
public interface TaskModule {
    @Binds
    @IntoMap
    @Singleton
    @StringKey("ENTITY_EXPIRATION")
    SystemTask bindEntityExpirationTask(ExpiryProcess expiryProcess);

    @Binds
    @IntoMap
    @Singleton
    @StringKey("TRACEABILITY_EXPORT")
    SystemTask bindTraceabilityExportTask(TraceabilityExportTask traceabilityExportTask);
}
