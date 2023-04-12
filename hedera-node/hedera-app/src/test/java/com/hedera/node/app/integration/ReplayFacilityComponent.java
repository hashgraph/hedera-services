package com.hedera.node.app.integration;

import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.hedera.node.app.services.ServiceModule;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.HandlersModule;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
        ServiceModule.class,
        HandlersModule.class,
        ReplayFacilityModule.class,
})
public interface ReplayFacilityComponent {
    @Component.Factory
    interface Factory {
        ReplayFacilityComponent create();
    }

    ReplayAssetRecording assetRecording();
    InMemoryWritableStoreFactory writableStoreFactory();
    ReplayFacilityTransactionDispatcher transactionDispatcher();
}
