/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.datasource;

import com.swirlds.common.io.SelfSerializable;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;

/**
 * Manages {@link VirtualDataSource} instances. An instance of a data source builder is provided
 * to every {@link com.swirlds.virtualmap.VirtualMap} and used to get a reference to underlying
 * virtual data source.
 *
 * <p>Virtual data source builder configuration is not a part of this interface. For example, some
 * implementations that store data on disk may have "storage directory" config, which is used,
 * together with requested data source labels, to build full data source disk paths.
 */
public interface VirtualDataSourceBuilder extends SelfSerializable {

    /**
     * Builds a new {@link VirtualDataSource} using the configuration of this builder and
     * the given label. If a data source with the given label already exists, it's used instead.
     *
     * @param label
     * 		The label. Cannot be null. Labels can be used in logs and stats, and also to build
     * 		full disk paths to store data source files. This is builder implementation specific
     * @return
     * 		An opened {@link VirtualDataSource}.
     */
    @NonNull
    VirtualDataSource build(String label);

    /**
     * Builds a new {@link VirtualDataSource} using the configuration of this builder by creating
     * a snapshot of the given data source. The new data source doesn't have background file
     * compaction enabled.
     *
     * <p>This method is used when a virtual map copy is created during reconnects. When a copy is
     * created on the teacher side, the original data source is preserved as active, i.e. used
     * to handle transactions. When this method is used on the learner side, it behaves in the
     * opposite way: the copied data source becomes active, while the original data source isn't
     * used any longer other than to re-initiate reconnect when failed.
     *
     * @param snapshotMe
     * 		The dataSource to invoke snapshot on. Cannot be null
     * @param makeCopyActive
     *      Indicates whether to make the copy active or keep the original data source active
     * @param offlineUse
     *      Indicates that the copied data source should use as little resources as possible. Data
     *      source copies created for offline use should not be used for performance critical tasks
     * @return
     * 		An opened {@link VirtualDataSource}
     */
    @NonNull
    VirtualDataSource copy(VirtualDataSource snapshotMe, boolean makeCopyActive, boolean offlineUse);

    /**
     * Builds a new {@link VirtualDataSource} using the configuration of this builder by creating
     * a snapshot of the given data source in the specified folder. The new data source doesn't
     * have background file compaction enabled. Such snapshots should not be used for any time
     * critical operations, since snapshot data sources are expected to consume as little resources
     * as possible (e.g. use on-disk rather than in-memory indices) and therefore may be slow.
     *
     * <p>This method is used when a virtual map is written to disk during state serialization.
     *
     * @param destination
     * 		The base path into which to snapshot the database. Can be null
     * @param snapshotMe
     * 		The dataSource to invoke snapshot on. Cannot be null
     */
    void snapshot(@NonNull Path destination, VirtualDataSource snapshotMe);

    /**
     * Builds a new {@link VirtualDataSource} using the configuration of this builder and
     * the given label by copying all the database files from the given path into the new
     * database directory and then opening that database.
     *
     * <p>This method is used when a virtual map is deserialized from a state snapshot.
     * for details.
     *
     * @param label
     * 		The label. Cannot be null. This label must be posix compliant
     * @param source
     * 		The base path of the database from which to copy all the database files. Cannot be null
     * @return
     * 		An opened {@link VirtualDataSource}
     */
    @NonNull
    VirtualDataSource restore(String label, Path source);
}
