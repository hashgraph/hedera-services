/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.addressbook.impl;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.addressbook.impl.schemas.InitialServiceNodeSchema;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Standard implementation of the {@link AddressBookService} {@link Service}.
 */
public final class AddressBookServiceImpl implements AddressBookService {
    public static final String NODES_KEY = "NODES";

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry, @NonNull final SemanticVersion version) {
        InitialServiceNodeSchema nodeSchema = new InitialServiceNodeSchema(version);
        registry.register(nodeSchema);
    }
}
