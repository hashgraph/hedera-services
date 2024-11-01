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

package com.hedera.node.app.info;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.annotations.NodeSelfId;
import com.swirlds.state.merkle.info.NodeInfo;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

/** A Dagger module for facilities in the {@link com.hedera.node.app.info} package. */
@Module
public abstract class InfoInjectionModule {

    @Provides
    @NodeSelfId
    static AccountID selfAccountID(@NonNull final NodeInfo info) {
        return info.accountId();
    }
}
