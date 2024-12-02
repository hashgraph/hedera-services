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

package com.hedera.services.bdd.junit.hedera;

import static com.hedera.services.bdd.junit.hedera.ExternalPath.UPGRADE_ARTIFACTS_DIR;
import static com.hedera.services.bdd.junit.hedera.subprocess.ProcessUtils.conditionFuture;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.recreateWorkingDir;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.LongFunction;

/**
 * Implementation support for a node that uses a local working directory.
 */
public abstract class AbstractLocalNode<T extends AbstractLocalNode<T>> extends AbstractNode implements HederaNode {
    /**
     * How many milliseconds to wait between re-checking if a marker file exists.
     */
    private static final long MF_BACKOFF_MS = 50L;
    /**
     * Whether the working directory has been initialized.
     */
    protected boolean workingDirInitialized;

    protected AbstractLocalNode(@NonNull final NodeMetadata metadata) {
        super(metadata);
    }

    @Override
    public @NonNull T initWorkingDir(
            @NonNull final String configTxt,
            @NonNull final LongFunction<Bytes> tssEncryptionKeyFn,
            @NonNull final Function<List<RosterEntry>, Optional<TssKeyMaterial>> tssKeyMaterialFn) {
        requireNonNull(configTxt);
        requireNonNull(tssEncryptionKeyFn);
        requireNonNull(tssKeyMaterialFn);
        recreateWorkingDir(requireNonNull(metadata.workingDir()), configTxt, tssEncryptionKeyFn, tssKeyMaterialFn);
        workingDirInitialized = true;
        return self();
    }

    protected void assertWorkingDirInitialized() {
        if (!workingDirInitialized) {
            throw new IllegalStateException("Working directory not initialized");
        }
    }

    @Override
    public CompletableFuture<Void> mfFuture(@NonNull final MarkerFile markerFile) {
        requireNonNull(markerFile);
        return conditionFuture(() -> mfExists(markerFile), () -> MF_BACKOFF_MS);
    }

    protected abstract T self();

    private boolean mfExists(@NonNull final MarkerFile markerFile) {
        return Files.exists(getExternalPath(UPGRADE_ARTIFACTS_DIR).resolve(markerFile.fileName()));
    }
}
