/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.prehandle;

import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.PreHandleDispatcher;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @deprecated This class is only needed to have a PreHandleDispatcher implementation that can be provided by dagger.
 */
@Deprecated(forRemoval = true)
@Singleton
public class DummyPreHandleDispatcher implements PreHandleDispatcher {

    @Inject
    public DummyPreHandleDispatcher() {}

    @Override
    public void dispatch(@NonNull final PreHandleContext context) throws PreCheckException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
