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
import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
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
                .virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
                .keySerializer(new BenchmarkKeySerializer())
                .storageDir(getTestDir().resolve("jasperdb"))
                .preferDiskBasedIndexes(false);
        final VirtualMap<BenchmarkKey, BenchmarkValue> createdMap = new VirtualMap<>(LABEL, diskDbBuilder);
        BenchmarkMetrics.register(createdMap::registerMetrics);
        return createdMap;
    }
}
