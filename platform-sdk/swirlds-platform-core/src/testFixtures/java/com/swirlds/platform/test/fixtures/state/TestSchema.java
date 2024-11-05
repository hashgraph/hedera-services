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

package com.swirlds.platform.test.fixtures.state;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * An implementation of {@link Schema} that takes an integer as the version and has no
 * implementation for the various methods. Test code can subclass this to add the behavior they'd
 * like to test with.
 */
public class TestSchema extends Schema {
    public static final SemanticVersion CURRENT_VERSION = new SemanticVersion(0, 47, 0, "SNAPSHOT", "");
    private final Runnable onMigrate;
    private final Runnable onRestart;
    private final Set<StateDefinition> statesToCreate;
    private final Set<String> statesToRemove;

    public TestSchema(int version) {
        this(SemanticVersion.newBuilder().major(version).build());
    }

    public TestSchema(int major, int minor, int patch) {
        this(SemanticVersion.newBuilder().major(major).minor(minor).patch(patch).build());
    }

    public TestSchema(SemanticVersion version) {
        this(version, null);
    }

    public TestSchema(SemanticVersion version, Runnable onMigrate) {
        this(version, Set.of(), Set.of(), onMigrate, null);
    }

    public TestSchema(
            @NonNull final SemanticVersion version,
            @NonNull final Set<StateDefinition> statesToCreate,
            @NonNull final Set<String> statesToRemove,
            @Nullable Runnable onMigrate,
            @Nullable Runnable onRestart) {
        super(version);
        this.onMigrate = onMigrate;
        this.onRestart = onRestart;
        this.statesToCreate = statesToCreate;
        this.statesToRemove = statesToRemove;
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        super.migrate(ctx);
        if (onMigrate != null) {
            onMigrate.run();
        }
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return statesToCreate;
    }

    @NonNull
    @Override
    public Set<String> statesToRemove() {
        return statesToRemove;
    }

    @Override
    public void restart(@NonNull MigrationContext ctx) {
        super.restart(ctx);
        if (onRestart != null) {
            onRestart.run();
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private SemanticVersion version = CURRENT_VERSION;
        private Runnable onMigrate;
        private Runnable onRestart;
        private final Set<StateDefinition> statesToCreate = new HashSet<>();
        private final Set<String> statesToRemove = new HashSet<>();

        public Builder version(SemanticVersion version) {
            this.version = version;
            return this;
        }

        public Builder majorVersion(int major) {
            this.version = version.copyBuilder().major(major).build();
            return this;
        }

        public Builder minorVersion(int minor) {
            this.version = version.copyBuilder().minor(minor).build();
            return this;
        }

        public Builder patchVersion(int patch) {
            this.version = version.copyBuilder().patch(patch).build();
            return this;
        }

        public Builder onMigrate(Runnable onMigrate) {
            this.onMigrate = onMigrate;
            return this;
        }

        public Builder onRestart(Runnable onRestart) {
            this.onRestart = onRestart;
            return this;
        }

        public Builder stateToCreate(StateDefinition state) {
            this.statesToCreate.add(state);
            return this;
        }

        public Builder stateToRemove(String state) {
            this.statesToRemove.add(state);
            return this;
        }

        public TestSchema build() {
            return new TestSchema(version, statesToCreate, statesToRemove, onMigrate, onRestart);
        }
    }
}
