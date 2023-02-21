/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.context.properties;

import static com.hedera.node.app.spi.config.PropertyNames.WORKFLOWS_ENABLED;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GlobalStaticProperties {

    private final PropertySource properties;
    private Set<HederaFunctionality> workflowsEnabled = new HashSet<>();

    @Inject
    public GlobalStaticProperties(@Nullable final BootstrapProperties properties) {
        this.properties = properties;
        reload();
    }

    public void reload() {
        if (properties != null) {
            workflowsEnabled = properties.getFunctionsProperty(WORKFLOWS_ENABLED);
        }
    }

    public Set<HederaFunctionality> workflowsEnabled() {
        return workflowsEnabled;
    }
}
