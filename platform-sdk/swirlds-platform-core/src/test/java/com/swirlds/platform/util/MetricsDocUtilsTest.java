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

package com.swirlds.platform.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.DefaultCounter;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Testing MetricsDocUtils")
class MetricsDocUtilsTest {

    private static final String METRIC_DOC_FILE_NAME = "myDoc.tsv";

    @Test
    @DisplayName("Should write a metrics document file in the base directory")
    void writeMetricsDocumentToFile() {
        // setup
        // Unable to mock BasicConfig.class because it is a final class, so using the test builder instead of mocking.
        final Configuration configuration = new TestConfigBuilder()
                .withValue("metricsDocFileName", METRIC_DOC_FILE_NAME)
                .getOrCreateConfig();
        final String docFilePath = ConfigUtils.getUserDir() + File.separator + METRIC_DOC_FILE_NAME;
        final File oldFile = new File(docFilePath);
        if (oldFile.exists()) {
            FileUtils.deleteQuietly(oldFile);
        }

        assertFalse(oldFile.exists(), "The metric document file should be removed before testing: " + docFilePath);

        // given
        final Metrics globalMetrics = mock(DefaultMetrics.class);
        final SwirldsPlatform platform1 = mock(SwirldsPlatform.class);
        final PlatformContext context1 = mock(PlatformContext.class);
        when(platform1.getContext()).thenReturn(context1);
        final SwirldsPlatform platform2 = mock(SwirldsPlatform.class);
        final PlatformContext context2 = mock(PlatformContext.class);
        when(platform2.getContext()).thenReturn(context2);
        final Metrics platform1Metrics = mock(DefaultMetrics.class);
        when(context1.getMetrics()).thenReturn(platform1Metrics);
        final Metrics platform2Metrics = mock(DefaultMetrics.class);
        when(context2.getMetrics()).thenReturn(platform2Metrics);
        final Collection<SwirldsPlatform> platforms = List.of(platform1, platform2);

        final Metric counterA = new DefaultCounter(new Counter.Config("category1", "metric.A")
                .withDescription("Metric A description")
                .withUnit("unit.A"));
        final Metric counterB = new DefaultCounter(new Counter.Config("category1", "metric.B")
                .withDescription("Metric B description")
                .withUnit("unit.B"));
        final Collection<Metric> globalMetricsList = List.of(counterA, counterB);

        final Metric counterC = new DefaultCounter(new Counter.Config("category2", "metric.C")
                .withDescription("Metric C description")
                .withUnit("unit.C"));
        final Metric counterD = new DefaultCounter(new Counter.Config("category2", "metric.D")
                .withDescription("Metric D description")
                .withUnit("unit.D"));
        final Metric counterE = new DefaultCounter(new Counter.Config("category3", "metric.E")
                .withDescription("Metric E description")
                .withUnit("unit.E"));
        final Collection<Metric> platformMetrics1List = List.of(counterD, counterC);
        final Collection<Metric> platformMetrics2List = List.of(counterC, counterD, counterE);

        // when
        when(globalMetrics.getAll()).thenReturn(globalMetricsList);

        when(platform1Metrics.getAll()).thenReturn(platformMetrics1List);
        when(platform2Metrics.getAll()).thenReturn(platformMetrics2List);

        MetricsDocUtils.writeMetricsDocumentToFile(globalMetrics, platforms, configuration);

        // result
        final File docFile = new File(docFilePath);
        assertTrue(docFile.exists(), "The metric document file was not created: " + docFilePath);

        final String expectedOutput =
                """
                Category	Identifier	Name	Metric Type	Data Type	Unit	Description
                category1	category1.metric.A	metric.A	COUNTER	INT	unit.A	Metric A description
                category1	category1.metric.B	metric.B	COUNTER	INT	unit.B	Metric B description
                category2	category2.metric.C	metric.C	COUNTER	INT	unit.C	Metric C description
                category2	category2.metric.D	metric.D	COUNTER	INT	unit.D	Metric D description
                category3	category3.metric.E	metric.E	COUNTER	INT	unit.E	Metric E description
                """;

        final String actualOutput = Files.contentOf(docFile, StandardCharsets.UTF_8);
        assertEquals(expectedOutput, actualOutput, "The metrics document contents was not generated correctly.");

        // teardown
        if (docFile.exists()) {
            FileUtils.deleteQuietly(docFile);
        }
    }
}
