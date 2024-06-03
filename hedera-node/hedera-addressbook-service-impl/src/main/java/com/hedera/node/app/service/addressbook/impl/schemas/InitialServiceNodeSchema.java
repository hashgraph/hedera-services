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

package com.hedera.node.app.service.addressbook.impl.schemas;

import static com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl.NODES_KEY;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * General schema for the addressbook service
 * (FUTURE) When mod-service release is finalized, rename this class to e.g.
 * {@code Release47addressbookSchema} as it will no longer be appropriate to assume
 * this schema is always correct for the current version of the software.
 */
public class InitialServiceNodeSchema extends Schema {
    private static final Logger log = LogManager.getLogger(InitialServiceNodeSchema.class);

    private static final long MAX_NODES = 1_000L;

    public InitialServiceNodeSchema(@NonNull final SemanticVersion version) {
        super(version);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.onDisk(NODES_KEY, EntityNumber.PROTOBUF, Node.PROTOBUF, MAX_NODES));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        throw new UnsupportedOperationException("need implementation");
    }
}
