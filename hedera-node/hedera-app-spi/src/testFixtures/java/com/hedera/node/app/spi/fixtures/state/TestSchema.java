/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.fixtures.state;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An implementation of {@link Schema} that takes an integer as the version and has no
 * implementation for the various methods. Test code can subclass this to add the behavior they'd
 * like to test with.
 */
public class TestSchema extends Schema {
    private Runnable onMigrate;

    public TestSchema(SemanticVersion version) {
        super(version);
    }

    public TestSchema(int version) {
        this(new SemanticVersion.Builder().major(version).build());
    }

    public TestSchema(int major, int minor, int patch) {
        this(new SemanticVersion.Builder().major(major).minor(minor).patch(patch).build());
    }

    public TestSchema(SemanticVersion version, Runnable onMigrate) {
        this(version);
        this.onMigrate = onMigrate;
    }

    @Override
    public void migrate(@NonNull ReadableStates previousStates, @NonNull WritableStates newStates) {
        super.migrate(previousStates, newStates);
        if (onMigrate != null) {
            onMigrate.run();
        }
    }
}
