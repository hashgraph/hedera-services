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

package com.hedera.node.app.service.mono.context;

import com.hedera.node.app.service.mono.ServicesApp;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public enum AppsManager {
    APPS;

    private final Map<NodeId, ServicesApp> apps = new HashMap<>();

    public boolean includes(@NonNull final NodeId nodeId) {
        return apps.containsKey(Objects.requireNonNull(nodeId));
    }

    public void save(@NonNull final NodeId id, @NonNull final ServicesApp app) {
        apps.put(Objects.requireNonNull(id), Objects.requireNonNull(app));
    }

    public void clear(@NonNull final NodeId id) {
        apps.remove(Objects.requireNonNull(id));
    }

    public ServicesApp get(@NonNull final NodeId id) {
        if (!includes(Objects.requireNonNull(id))) {
            throw new IllegalArgumentException("No app saved for node " + id);
        }
        return apps.get(id);
    }
}
