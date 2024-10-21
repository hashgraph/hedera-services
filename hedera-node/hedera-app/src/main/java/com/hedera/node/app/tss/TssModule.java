/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import com.hedera.hapi.platform.state.NodeId;
import com.hedera.node.app.annotations.CommonExecutor;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.state.listeners.ReconnectListener;
import com.hedera.node.app.state.listeners.WriteStateToDiskListener;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.tss.PlaceholderTssLibrary;
import com.swirlds.common.stream.Signer;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.system.Platform;
import com.swirlds.state.State;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Singleton;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@Module
public interface TssModule {
    @Provides
    @Singleton
    static TssCryptographyManager tssCryptographyManager(
            @NonNull final WorkingStateAccessor workingStateAccessor,
            @NonNull final Platform platform) {
        return new TssCryptographyManager(platform.getSelfId().id(),
                new PlaceholderTssLibrary(),
                new TssParticipantDirectory(),
                );
    }
}
