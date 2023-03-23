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

package com.swirlds.benchmark;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.PathHashRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.virtualmap.VirtualMap;
import org.openjdk.jmh.annotations.Setup;

public class CryptoBenchJPDB extends CryptoBench {

    @Setup
    public static void setupJasperDB() throws Exception {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.jasperdb");
    }

    protected VirtualMap<BenchmarkKey, BenchmarkValue> createEmptyMap() {
        final VirtualLeafRecordSerializer<BenchmarkKey, BenchmarkValue> virtualLeafRecordSerializer =
                new VirtualLeafRecordSerializer<>(
                        (short) 1,
                        BenchmarkKey.getSerializedSize(),
                        new BenchmarkKeySupplier(),
                        (short) 1,
                        BenchmarkValue.getSerializedSize(),
                        new BenchmarkValueSupplier(),
                        true);
        final JasperDbBuilder<BenchmarkKey, BenchmarkValue> diskDbBuilder = new JasperDbBuilder<>();
        diskDbBuilder
                .virtualLeafRecordSerializer(virtualLeafRecordSerializer)
                .virtualInternalRecordSerializer(new PathHashRecordSerializer())
                .keySerializer(new BenchmarkKeySerializer())
                .storageDir(getTestDir().resolve("jasperdb"))
                .preferDiskBasedIndexes(false);
        return new VirtualMap<>(LABEL, diskDbBuilder);
    }

    public static void main(final String[] args) throws Exception {
        final CryptoBenchJPDB bench = new CryptoBenchJPDB();
        bench.numFiles = 10;
        bench.numRecords = 100000;
        bench.maxKey = 1000000;
        bench.keySize = 8;
        bench.recordSize = 24;
        bench.setup();
        bench.setupJasperDB();
        for (int i = 0; i < 1; i++) {
            bench.beforeTest();
            bench.transferSerial();
            bench.afterTest();
        }
        bench.destroy();
    }
}
