/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.tasks;

import com.hedera.services.state.expiry.ExpiryProcess;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import javax.inject.Singleton;

/**
 * Binds the {@link SystemTask} implementations, which should always include {@link ExpiryProcess},
 * but may include others such as the 0.31.x {@link TraceabilityExportTask}.
 *
 * <p>Note we are keeping {@link TraceabilityExportTask} in the codebase at this time for two
 * reasons:
 *
 * <ol>
 *   <li>It is somewhat likely that we'll want to run it again; and,
 *   <li>It provides a much simpler model task than expiration, which uses dozens of classes and
 *       thus obscures some of the details of the task framework.
 * </ol>
 */
@Module
public interface TaskModule {
    @Binds
    @IntoMap
    @Singleton
    @StringKey("1_ENTITY_EXPIRATION")
    SystemTask bindEntityExpirationTask(ExpiryProcess expiryProcess);
}
