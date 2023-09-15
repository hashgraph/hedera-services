/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import edu.umd.cs.findbugs.annotations.NonNull;

public class ServicesSwirldMain implements SwirldMain {

    @Override
    public void init(@NonNull final Platform platform, @NonNull final NodeId selfId) {}

    @Override
    public void run() {}

    @Override
    public SwirldState newState() {
        return null;
    }

    @Override
    public SoftwareVersion getSoftwareVersion() {
        return null;
    }
}
