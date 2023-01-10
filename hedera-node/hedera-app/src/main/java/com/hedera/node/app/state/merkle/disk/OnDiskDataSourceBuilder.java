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
package com.hedera.node.app.state.merkle.disk;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.JasperDbBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/** This class is a workaround for Swirlds issue #6476 */
public class OnDiskDataSourceBuilder<K extends Comparable<K>, V>
        extends JasperDbBuilder<OnDiskKey<K>, OnDiskValue<V>> {
    private static final long CLASS_ID = -2020188928382075700L;

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        super.serialize(out);

        try {
            // This is a workaround for Swirlds issue #6476. The storageDir is not being serialized.
            // And the getter for storageDir is private. So time for some reflection.
            final var clazz = JasperDbBuilder.class;
            final var storageDirField = clazz.getDeclaredField("storageDir");
            storageDirField.setAccessible(true);
            final var storageDir = (Path) storageDirField.get(this);
            final var asString = storageDir.toFile().getAbsolutePath();
            out.writeNormalisedString(asString);
        } catch (Exception e) {
            throw new RuntimeException(
                    "FATAL: Unable to write Virtual DataSource Builder to disk", e);
        }
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        super.deserialize(in, version);

        try {
            // This is a workaround for Swirlds issue #6476. The storageDir is not being serialized.
            // And there is no setter for storageDir and the field is private. So time for some
            // reflection.
            final var asString = in.readNormalisedString(1024);
            final var clazz = JasperDbBuilder.class;
            final var storageDirField = clazz.getDeclaredField("storageDir");
            storageDirField.setAccessible(true);
            storageDirField.set(this, new File(asString).toPath());
        } catch (Exception e) {
            throw new RuntimeException(
                    "FATAL: Unable to read Virtual DataSource Builder from disk", e);
        }
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }
}
