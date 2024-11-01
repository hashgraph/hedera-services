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

package com.hedera.node.app.fees;

import com.hedera.node.app.fees.schemas.V0490FeeSchema;
import com.swirlds.state.merkle.SchemaRegistry;
import com.swirlds.state.merkle.Service;
import edu.umd.cs.findbugs.annotations.NonNull;

public class FeeService implements Service {
    public static final String NAME = "FeeService";

    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        registry.register(new V0490FeeSchema());
    }
}
