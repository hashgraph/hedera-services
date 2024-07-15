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

package com.hedera.node.app.ids;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.ids.EntityNumGenerator;
import com.hedera.node.app.store.WritableStoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Default implementation of {@link EntityNumGenerator}.
 */
public class EntityNumGeneratorImpl implements EntityNumGenerator {

    private final WritableStoreFactory factory;

    public EntityNumGeneratorImpl(@NonNull final WritableStoreFactory factory) {
        this.factory = requireNonNull(factory);
    }

    @Override
    public long newEntityNum() {
        return factory.getStore(WritableEntityIdStore.class).incrementAndGet();
    }

    @Override
    public long peekAtNewEntityNum() {
        return factory.getStore(WritableEntityIdStore.class).peekAtNextNumber();
    }
}
