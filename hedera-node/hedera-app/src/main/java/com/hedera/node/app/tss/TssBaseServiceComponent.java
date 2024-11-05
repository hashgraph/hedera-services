/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.tss;

import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.handlers.TssMessageHandler;
import com.hedera.node.app.tss.handlers.TssShareSignatureHandler;
import com.hedera.node.app.tss.handlers.TssSubmissions;
import com.hedera.node.app.tss.handlers.TssVoteHandler;
import com.swirlds.metrics.api.Metrics;
import dagger.BindsInstance;
import dagger.Component;
import java.time.InstantSource;
import java.util.concurrent.Executor;
import javax.inject.Singleton;

@Singleton
@Component
public interface TssBaseServiceComponent {
    @Component.Factory
    interface Factory {
        TssBaseServiceComponent create(
                @BindsInstance TssLibrary tssLibrary,
                @BindsInstance InstantSource instantSource,
                @BindsInstance AppContext.Gossip gossip,
                @BindsInstance Executor submissionExecutor,
                @BindsInstance @TssLibraryExecutor Executor libraryExecutor,
                @BindsInstance Metrics metrics,
                @BindsInstance PrivateKeysAccessor privateKeysAccessor);
    }

    TssMetrics tssMetrics();

    TssMessageHandler tssMessageHandler();

    TssVoteHandler tssVoteHandler();

    TssShareSignatureHandler tssShareSignatureHandler();

    TssSubmissions tssSubmissions();
}
