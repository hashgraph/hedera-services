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
package com.hedera.services.stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.services.context.properties.NodeLocalProperties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.mockito.Mockito;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 10, time = 30)
public class NonBlockingHandoffBench {

    private NonBlockingHandoff nonBlockingHandoff;
    private NodeLocalProperties nodeLocalProperties;
    private RecordStreamManager recordStreamManager;
    private BlockingQueue<RecordStreamObject> receivingQueue;

    @Setup(Level.Trial)
    public void setupInfrastructure() {
        receivingQueue = new LinkedBlockingQueue<>();
        nodeLocalProperties = mock(NodeLocalProperties.class, Mockito.withSettings().stubOnly());
        recordStreamManager = mock(RecordStreamManager.class, Mockito.withSettings().stubOnly());
        when(nodeLocalProperties.recordStreamQueueCapacity()).thenReturn(5000);
        doAnswer(val -> receivingQueue.add(val.getArgument(0, RecordStreamObject.class)))
                .when(recordStreamManager)
                .addRecordStreamObject(any());
        nonBlockingHandoff = new NonBlockingHandoff(recordStreamManager, nodeLocalProperties);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        nonBlockingHandoff.getExecutor().shutdownNow();
    }

    @Benchmark
    public void simpleProcessing() throws InterruptedException {
        nonBlockingHandoff.offer(new RecordStreamObject());
        receivingQueue.take();
    }
}
