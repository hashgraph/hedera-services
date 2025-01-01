/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.history;

import com.hedera.node.app.history.handlers.HistoryAssemblySignatureHandler;
import com.hedera.node.app.history.handlers.HistoryHandlers;
import com.hedera.node.app.history.handlers.HistoryProofKeyPublicationHandler;
import com.hedera.node.app.history.handlers.HistoryProofVoteHandler;
import com.hedera.node.app.history.impl.ProofKeysAccessorImpl;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Singleton;

@Module
public interface HistoryModule {
    @Binds
    @Singleton
    ProofKeysAccessor bindProofKeyAccessor(@NonNull ProofKeysAccessorImpl proofKeysAccessorImpl);

    @Provides
    @Singleton
    static HistoryHandlers provideHistoryHandlers(
            @NonNull final HistoryAssemblySignatureHandler historyAssemblySignatureHandler,
            @NonNull final HistoryProofKeyPublicationHandler historyProofKeyPublicationHandler,
            @NonNull final HistoryProofVoteHandler historyProofVoteHandler) {
        return new HistoryHandlers(
                historyAssemblySignatureHandler, historyProofKeyPublicationHandler, historyProofVoteHandler);
    }
}
