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

package com.swirlds.common.threading.framework.internal;

import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.metrics.IntegerAccumulator;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsFactory;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.manager.ThreadBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbstractQueueThreadConfigurationTest {

    /**
     * Dummy QueueThreadConfiguration for testing
     */
    static class DummyQueueThreadConfiguration<T>
            extends AbstractQueueThreadConfiguration<DummyQueueThreadConfiguration<T>, T> {

        protected DummyQueueThreadConfiguration(final ThreadBuilder threadBuilder) {
            super(threadBuilder);
        }

        protected DummyQueueThreadConfiguration(
                final AbstractQueueThreadConfiguration<DummyQueueThreadConfiguration<T>, T> that) {
            super(that);
        }

        @Override
        public DummyQueueThreadConfiguration<T> copy() {
            return new DummyQueueThreadConfiguration<>(this);
        }
    }

    static final long NODE_ID = 1L;
    static final String THREAD_POOL_NAME = "myThreadPool";
    static final String THREAD_NAME = "myThread";
    static final int MAX_BUFFER_SIZE = 50;
    static final int CAPACITY = 10;
    static final String MAX_SIZE_METRIC_NAME = THREAD_NAME + MeasuredBlockingQueue.QUEUE_MAX_SIZE_SUFFIX;
    static final String MIN_SIZE_METRIC_NAME = THREAD_NAME + MeasuredBlockingQueue.QUEUE_MIN_SIZE_SUFFIX;

    private Metrics metrics;

    @BeforeEach
    void setUp() {
        final MetricKeyRegistry registry = new MetricKeyRegistry();
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final MetricsFactory factory = new DefaultMetricsFactory();
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        metrics = new DefaultMetrics(null, registry, executor, factory, metricsConfig);
    }

    @Test
    @DisplayName("Testing constructor with thread manager and metrics")
    void constructorWithThreadManagerAndMetrics() {
        // given
        final ThreadBuilder threadManager = mock(ThreadBuilder.class);
        final InterruptableConsumer<String> handler = mock(InterruptableConsumer.class);
        final InterruptableRunnable waitForItemRunnable = mock(InterruptableRunnable.class);

        // when
        final DummyQueueThreadConfiguration<String> configuration = new DummyQueueThreadConfiguration<String>(
                        threadManager)
                .setNodeId(NODE_ID)
                .setComponent(THREAD_POOL_NAME)
                .setThreadName(THREAD_NAME)
                .setMaxBufferSize(MAX_BUFFER_SIZE)
                .setCapacity(CAPACITY)
                .setHandler(handler)
                .setWaitForItemRunnable(waitForItemRunnable);

        // then
        assertThat(configuration.getNodeId()).isEqualTo(NODE_ID);
        assertThat(configuration.getComponent()).isEqualTo(THREAD_POOL_NAME);
        assertThat(configuration.getThreadName()).isEqualTo(THREAD_NAME);
        assertThat(configuration.getMaxBufferSize()).isEqualTo(MAX_BUFFER_SIZE);
        assertThat(configuration.getCapacity()).isEqualTo(CAPACITY);
        assertThat(configuration.getHandler()).isEqualTo(handler);
        assertThat(configuration.getWaitForItemRunnable()).isEqualTo(waitForItemRunnable);
        assertThat(configuration.getQueue()).isNull();
    }

    @Test
    @DisplayName("Testing constructor with other configuration")
    void constructorWithOtherConfiguration() {
        // given
        final ThreadBuilder threadBuilder = mock(ThreadBuilder.class);
        final InterruptableConsumer<String> handler = mock(InterruptableConsumer.class);
        final InterruptableRunnable waitForItemRunnable = mock(InterruptableRunnable.class);
        final DummyQueueThreadConfiguration<String> configuration = new DummyQueueThreadConfiguration<String>(
                        threadBuilder)
                .setNodeId(NODE_ID)
                .setComponent(THREAD_POOL_NAME)
                .setThreadName(THREAD_NAME)
                .setMaxBufferSize(MAX_BUFFER_SIZE)
                .setCapacity(CAPACITY)
                .setHandler(handler)
                .setWaitForItemRunnable(waitForItemRunnable);

        // when
        final DummyQueueThreadConfiguration<String> copied = new DummyQueueThreadConfiguration<>(configuration);

        // then
        assertThat(configuration.getNodeId()).isEqualTo(copied.getNodeId());
        assertThat(configuration.getComponent()).isEqualTo(copied.getComponent());
        assertThat(configuration.getThreadName()).isEqualTo(copied.getThreadName());
        assertThat(configuration.getMaxBufferSize()).isEqualTo(copied.getMaxBufferSize());
        assertThat(configuration.getCapacity()).isEqualTo(copied.getCapacity());
        assertThat(configuration.getHandler()).isEqualTo(copied.getHandler());
        assertThat(configuration.getWaitForItemRunnable()).isEqualTo(copied.getWaitForItemRunnable());
        assertThat(configuration.getQueue()).isEqualTo(copied.getQueue());
    }

    @Test
    @DisplayName("Testing build with start = false")
    void buildShouldCreateQueueThreadThatDoesNotStart() {
        // given
        final ThreadBuilder threadBuilder = mock(ThreadBuilder.class);
        final InterruptableConsumer<String> handler = mock(InterruptableConsumer.class);
        final InterruptableRunnable waitForItemRunnable = mock(InterruptableRunnable.class);

        // when
        final DummyQueueThreadConfiguration<String> configuration = new DummyQueueThreadConfiguration<String>(
                        threadBuilder)
                .setNodeId(NODE_ID)
                .setComponent(THREAD_POOL_NAME)
                .setThreadName(THREAD_NAME)
                .setMaxBufferSize(MAX_BUFFER_SIZE)
                .setCapacity(CAPACITY)
                .setHandler(handler)
                .setWaitForItemRunnable(waitForItemRunnable);
        final QueueThread<String> queueThread = configuration.buildQueueThread(false);

        // then
        assertThat(configuration.getQueue()).isInstanceOf(LinkedBlockingQueue.class);
        assertThat(queueThread.getName()).isEqualTo(THREAD_NAME);
        assertThat(queueThread.getStatus()).isEqualTo(StoppableThread.Status.NOT_STARTED);
    }

    @Test
    @DisplayName("Testing build with start = true")
    void buildWithStartShouldCreateQueueThreadThatStarts() {
        // given
        final ThreadBuilder threadBuilder = mock(ThreadBuilder.class);
        final InterruptableConsumer<String> handler = mock(InterruptableConsumer.class);
        final InterruptableRunnable waitForItemRunnable = mock(InterruptableRunnable.class);

        // when
        when(threadBuilder.buildThread(any(Runnable.class))).thenReturn(new Thread());
        final DummyQueueThreadConfiguration<String> configuration = new DummyQueueThreadConfiguration<String>(
                        threadBuilder)
                .setNodeId(NODE_ID)
                .setComponent(THREAD_POOL_NAME)
                .setThreadName(THREAD_NAME)
                .setMaxBufferSize(MAX_BUFFER_SIZE)
                .setCapacity(CAPACITY)
                .setHandler(handler)
                .setWaitForItemRunnable(waitForItemRunnable);
        final QueueThread<String> queueThread = configuration.buildQueueThread(true);

        // then
        assertThat(configuration.getQueue()).isInstanceOf(LinkedBlockingQueue.class);
        assertThat(queueThread.getName()).isEqualTo(THREAD_NAME);
        assertThat(queueThread.getStatus()).isEqualTo(StoppableThread.Status.ALIVE);
    }

    @Test
    @DisplayName("Testing build with external queue")
    void buildShouldCreateQueueThreadWithExternalQueue() {
        // given
        final ThreadBuilder threadBuilder = mock(ThreadBuilder.class);
        final InterruptableConsumer<String> handler = mock(InterruptableConsumer.class);
        final InterruptableRunnable waitForItemRunnable = mock(InterruptableRunnable.class);
        final BlockingQueue<String> queue = new PriorityBlockingQueue<>(List.of("A", "B", "C"));

        // when
        final DummyQueueThreadConfiguration<String> configuration = new DummyQueueThreadConfiguration<String>(
                        threadBuilder)
                .setNodeId(NODE_ID)
                .setComponent(THREAD_POOL_NAME)
                .setThreadName(THREAD_NAME)
                .setMaxBufferSize(MAX_BUFFER_SIZE)
                .setCapacity(CAPACITY)
                .setHandler(handler)
                .setWaitForItemRunnable(waitForItemRunnable)
                .setQueue(queue);
        final QueueThread<String> queueThread = configuration.buildQueueThread(false);

        // then
        assertThat(configuration.getQueue()).isInstanceOf(PriorityBlockingQueue.class);
        assertThat(queueThread.getName()).isEqualTo(THREAD_NAME);
        assertThat(queueThread).hasSize(3).containsExactly("A", "B", "C");

        // when
        final IntegerAccumulator maxSizeMetric =
                (IntegerAccumulator) metrics.getMetric(INTERNAL_CATEGORY, MAX_SIZE_METRIC_NAME);
        final IntegerAccumulator minSizeMetric =
                (IntegerAccumulator) metrics.getMetric(INTERNAL_CATEGORY, MIN_SIZE_METRIC_NAME);

        // then
        assertThat(maxSizeMetric).isNull();
        assertThat(minSizeMetric).isNull();
    }

    @Test
    @DisplayName("Testing build with metric enabled")
    void buildShouldCreateQueueThreadThatCanBeMonitoredWithMetrics() {
        // given
        final ThreadBuilder threadBuilder = mock(ThreadBuilder.class);
        final InterruptableConsumer<String> handler = mock(InterruptableConsumer.class);
        final InterruptableRunnable waitForItemRunnable = mock(InterruptableRunnable.class);
        final BlockingQueue<String> queue = new LinkedBlockingQueue<>(List.of("A", "B", "C"));

        // when
        final DummyQueueThreadConfiguration<String> configuration = new DummyQueueThreadConfiguration<String>(
                        threadBuilder)
                .setNodeId(NODE_ID)
                .setComponent(THREAD_POOL_NAME)
                .setThreadName(THREAD_NAME)
                .setMaxBufferSize(MAX_BUFFER_SIZE)
                .setCapacity(CAPACITY)
                .setHandler(handler)
                .setWaitForItemRunnable(waitForItemRunnable)
                .setQueue(queue)
                .enableMaxSizeMetric(metrics)
                .enableMinSizeMetric(metrics);
        final QueueThread<String> queueThread = configuration.buildQueueThread(false);

        // then
        assertThat(queueThread).hasSize(3);
        assertThat(configuration.getQueue()).isInstanceOf(MeasuredBlockingQueue.class);

        // when
        final IntegerAccumulator maxSizeMetric =
                (IntegerAccumulator) metrics.getMetric(INTERNAL_CATEGORY, MAX_SIZE_METRIC_NAME);
        final IntegerAccumulator minSizeMetric =
                (IntegerAccumulator) metrics.getMetric(INTERNAL_CATEGORY, MIN_SIZE_METRIC_NAME);

        // then
        assertThat(maxSizeMetric).isNotNull();
        assertThat(maxSizeMetric.get()).isEqualTo(3);
        assertThat(minSizeMetric).isNotNull();
        assertThat(minSizeMetric.get()).isEqualTo(3);

        // when
        queueThread.add("D");
        queueThread.poll();
        queueThread.poll();
        queueThread.poll();
        queueThread.add("E");

        // then
        assertThat(queueThread).hasSize(2);
        assertThat(maxSizeMetric.get()).isEqualTo(4);
        assertThat(minSizeMetric.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Testing build with metric enabled with null metrics")
    void buildShouldExceptionIfMetricEnabledWithNullMetrics() {
        // given
        final ThreadBuilder threadBuilder = mock(ThreadBuilder.class);
        final InterruptableConsumer<String> handler = mock(InterruptableConsumer.class);
        final InterruptableRunnable waitForItemRunnable = mock(InterruptableRunnable.class);

        // then
        assertThatThrownBy(() -> new DummyQueueThreadConfiguration<String>(threadBuilder)
                        .setNodeId(NODE_ID)
                        .setComponent(THREAD_POOL_NAME)
                        .setThreadName(THREAD_NAME)
                        .setMaxBufferSize(MAX_BUFFER_SIZE)
                        .setCapacity(CAPACITY)
                        .setHandler(handler)
                        .setWaitForItemRunnable(waitForItemRunnable)
                        .enableMaxSizeMetric(null)
                        .buildQueueThread(false))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new DummyQueueThreadConfiguration<String>(threadBuilder)
                        .setNodeId(NODE_ID)
                        .setComponent(THREAD_POOL_NAME)
                        .setThreadName(THREAD_NAME)
                        .setMaxBufferSize(MAX_BUFFER_SIZE)
                        .setCapacity(CAPACITY)
                        .setHandler(handler)
                        .setWaitForItemRunnable(waitForItemRunnable)
                        .enableMinSizeMetric(null)
                        .buildQueueThread(false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
