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
package com.hedera.services.context;

import com.hedera.services.ServicesApp;
import java.util.HashMap;
import java.util.Map;

public enum AppsManager {
    APPS;

    private final Map<Long, ServicesApp> apps = new HashMap<>();

    public boolean includes(long nodeId) {
        return apps.containsKey(nodeId);
    }

    public void save(long id, ServicesApp app) {
        apps.put(id, app);
    }

    public void clear(long id) {
        apps.remove(id);
    }

    public ServicesApp get(long id) {
        if (!includes(id)) {
            throw new IllegalArgumentException("No app saved for node " + id);
        }
        return apps.get(id);
    }
}
