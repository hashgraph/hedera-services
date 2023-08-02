/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.MERKLE_DB;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

abstract class CompactionTask implements Callable<Boolean> {

    private static final Logger logger = LogManager.getLogger(CompactionTask.class);

    final String id;

    public CompactionTask(String id) {
        requireNonNull(id);
        this.id = id;
    }

    protected abstract boolean doCompaction() throws IOException, InterruptedException;

    @Override
    public Boolean call() throws Exception {
        try {
            return doCompaction();
        } catch (final InterruptedException | ClosedByInterruptException e) {
            Thread.currentThread().interrupt();
            logger.info(MERKLE_DB.getMarker(), "Interrupted while compacting, this is allowed.", e);
        } catch (Throwable e) {
            // It is important that we capture all exceptions here, otherwise a single exception
            // will stop all  future merges from happening.
            logger.error(EXCEPTION.getMarker(), "[{}] Compaction failed", id, e);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof CompactionTask task) {
            return id.equals(task.id);
        }

        return false;
    }
}
