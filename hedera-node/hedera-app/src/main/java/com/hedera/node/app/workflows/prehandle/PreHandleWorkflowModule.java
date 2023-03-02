package com.hedera.node.app.workflows.prehandle;

import com.hedera.node.app.signature.MonoSignaturePreparer;
import com.hedera.node.app.signature.SignaturePreparer;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

@Module
public interface PreHandleWorkflowModule {
    @Binds
    PreHandleWorkflow bindPreHandleWorkflow(PreHandleWorkflowImpl preHandleWorkflow);

    @Binds
    SignaturePreparer bindSignaturePreparer(MonoSignaturePreparer signaturePreparer);

    @Provides
    static ExecutorService provideExecutorService() {
        return ForkJoinPool.commonPool();
    }
}
