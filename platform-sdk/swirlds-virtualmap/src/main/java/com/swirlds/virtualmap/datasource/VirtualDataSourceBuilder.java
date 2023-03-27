/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import java.nio.file.Path;

/**
 * Manages {@link VirtualDataSource} instances. An instance of a data source builder is provided
 * to every {@link com.swirlds.virtualmap.VirtualMap} and used to get a reference to underlying
 * virtual data source.
 *
 * Virtual data source builder configuration is not a part of this interface. For example, some
 * implementations that store data on disk may have "storage directory" config, which is used,
 * together with requested data source labels, to build full data source disk paths.
 *
 * @param <K>
 * 		The key
 * @param <V>
 * 		The value
 */
public interface VirtualDataSourceBuilder<K extends VirtualKey<? super K>, V extends VirtualValue>
        extends SelfSerializable {

    /**
     * Builds a new {@link VirtualDataSource} using the configuration of this builder and
     * the given label. If a data source with the given label already exists, it's used instead.
     *
     * @param label
     * 		The label. Cannot be null. Labels can be used in logs and stats, and also to build
     * 		full disk paths to store data source files. This is builder implementation specific
     * @param withDbCompactionEnabled
     * 		If true then the new database will have background compaction enabled, false and the
     * 		new database will not have background compaction enabled
     * @return
     * 		An opened {@link VirtualDataSource}.
     */
    VirtualDataSource<K, V> build(String label, final boolean withDbCompactionEnabled);

    /**
     * Builds a new {@link VirtualDataSource} using the configuration of this builder by creating
     * a snapshot of the given data source. The new data source doesn't have background file
     * compaction enabled.
     *
     * This method is used when a virtual map copy is created during reconnects. When a copy is
     * created on the teacher side, the original data source is preserved as active, i.e. used
     * to handle transactions. When this method is used on the learner side, it behaves in the
     * opposite way: the copied data source becomes active, while the original data source isn't
     * used any longer other than to re-initiate reconnect when failed.
     *
     * @param snapshotMe
     * 		The dataSource to invoke snapshot on. Cannot be null
     * @param makeCopyActive
     *      Indicates whether to make the copy active or keep the original data source active
     * @return
     * 		An opened {@link VirtualDataSource}
     */
    VirtualDataSource<K, V> copy(VirtualDataSource<K, V> snapshotMe, boolean makeCopyActive);

    /**
     * Builds a new {@link VirtualDataSource} using the configuration of this builder by creating
     * a snapshot of the given data source in the specified folder. If the destination folder is
     * {@code null}, the snapshot is taken into an unspecified, usually temp, folder. The new data
     * source doesn't have background file compaction enabled.
     *
     * This method is used when a virtual map is written to disk during state serialization.
     *
     * @param destination
     * 		The base path into which to snapshot the database. Can be null
     * @param snapshotMe
     * 		The dataSource to invoke snapshot on. Cannot be null
     */
    void snapshot(Path destination, VirtualDataSource<K, V> snapshotMe);

    /**
     * Builds a new {@link VirtualDataSource} using the configuration of this builder and
     * the given label by copying all the database files from the given path into the new
     * database directory and then opening that database.
     *
     * This method is used when a virtual map is deserialized from a state snapshot.
     * for details.
     *
     * @param label
     * 		The label. Cannot be null. This label must be posix compliant
     * @param source
     * 		The base path of the database from which to copy all the database files. Cannot be null
     * @return
     * 		An opened {@link VirtualDataSource}
     */
    VirtualDataSource<K, V> restore(String label, Path source);
}
