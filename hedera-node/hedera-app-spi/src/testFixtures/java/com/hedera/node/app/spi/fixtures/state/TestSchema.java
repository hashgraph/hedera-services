/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.WritableStates;
import com.hederahashgraph.api.proto.java.SemanticVersion;
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
        this(SemanticVersion.newBuilder().setMajor(version).build());
    }

    public TestSchema(int major, int minor, int patch) {
        this(SemanticVersion.newBuilder()
                .setMajor(major)
                .setMinor(minor)
                .setPatch(patch)
                .build());
    }

    public TestSchema(SemanticVersion version, Runnable onMigrate) {
        this(version);
        this.onMigrate = onMigrate;
    }

    @Override
    public void migrate(@NonNull ReadableStates previousStates, @NonNull WritableStates newStates) {
        if (onMigrate != null) {
            onMigrate.run();
        }
    }
}
