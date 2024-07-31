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

package com.hedera.node.app.state.merkle;

import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A callback that is invoked when the state is initialized.
 */
public interface OnStateInitialized {
    void onStateInitialized(
            @NonNull MerkleStateRoot state,
            @NonNull Platform platform,
            @NonNull PlatformState platformState,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion deserializedVersion);
}
