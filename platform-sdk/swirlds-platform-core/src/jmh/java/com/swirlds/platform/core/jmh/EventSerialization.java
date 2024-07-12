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

package com.swirlds.platform.core.jmh;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.hashing.EventHasher;
import com.swirlds.platform.event.hashing.PbjHasher;
import com.swirlds.platform.event.hashing.StatefulEventHasher;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 10)
public class EventSerialization {

    @Param({"0"})
    public long seed;

    private PlatformEvent event;
    private MerkleDataOutputStream outStream;
    private MerkleDataInputStream inStream;
    private EventHasher oldEventHasher;
    private EventHasher newEventHasher;

    @Setup
    public void setup() throws IOException, ConstructableRegistryException {
        final Random random = new Random(seed);

        event = new TestingEventBuilder(random).setAppTransactionCount(20).setSystemTransactionCount(10).build();
        StaticSoftwareVersion.setSoftwareVersion(
                new BasicSoftwareVersion(event.getSoftwareVersion().major()));
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.platform.system");
        final PipedInputStream inputStream = new PipedInputStream();
        final PipedOutputStream outputStream = new PipedOutputStream(inputStream);
        outStream = new MerkleDataOutputStream(outputStream);
        inStream = new MerkleDataInputStream(inputStream);
        oldEventHasher = new StatefulEventHasher();
        newEventHasher = new PbjHasher();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void serializeDeserialize(final Blackhole bh) throws IOException {
        // results on Lazar's M1 Max MacBook Pro:
        //
        // Benchmark                                (seed)   Mode  Cnt    Score    Error   Units
        // EventSerialization.serializeDeserialize       0  thrpt    3  962.486 ± 29.252  ops/ms
        outStream.writeSerializable(event, false);
        bh.consume(inStream.readSerializable(false, PlatformEvent::new));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void hashingBase(final Blackhole bh) throws IOException {
        // results on Timo's M3 Max MacBook Pro:
        //
        // With 2 app and 1 system transactions:
        // Benchmark                       (seed)   Mode  Cnt     Score     Error   Units
        // EventSerialization.hashingBase       0  thrpt    3  1680.478 ± 173.379  ops/ms
        //
        // With 20 app and 10 system transactions:
        // Benchmark                       (seed)   Mode  Cnt    Score    Error   Units
        // EventSerialization.hashingBase       0  thrpt    3  236.143 ± 36.057  ops/ms
        bh.consume(oldEventHasher.hashEvent(event));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void hashingNew(final Blackhole bh) throws IOException {
        // results on Timo's M3 Max MacBook Pro:
        //
        // With 2 app and 1 system transactions:
        // Benchmark                      (seed)   Mode  Cnt    Score    Error   Units
        // EventSerialization.hashingNew       0  thrpt    3  991.056 ± 75.874  ops/ms (~ 60 %)
        // With 20 app and 10 system transactions:
        // Benchmark                      (seed)   Mode  Cnt    Score    Error   Units
        // EventSerialization.hashingNew       0  thrpt    3  121.640 ± 18.174  ops/ms (~ 50 %)

        bh.consume(newEventHasher.hashEvent(event));
    }
}
