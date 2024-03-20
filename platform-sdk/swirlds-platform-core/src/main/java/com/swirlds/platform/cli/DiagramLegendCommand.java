/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.cli;

import com.swirlds.base.time.Time;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.wiring.model.ModelEdgeSubstitution;
import com.swirlds.common.wiring.model.ModelGroup;
import com.swirlds.common.wiring.model.ModelManualLink;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.SolderType;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.DefaultConfiguration;
import com.swirlds.platform.util.BootstrapUtils;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import picocli.CommandLine;

@CommandLine.Command(
        name = "legend",
        mixinStandardHelpOptions = true,
        description = "Generate a legend for the mermaid style diagrams of platform wiring.")
@SubcommandOf(DiagramCommand.class)
public final class DiagramLegendCommand extends AbstractCommand {
    private DiagramLegendCommand() {}

    /**
     * Entry point.
     */
    @Override
    public Integer call() throws IOException {

        final Configuration configuration = DefaultConfiguration.buildBasicConfiguration();
        BootstrapUtils.setupConstructableRegistry();

        final PlatformContext platformContext = new DefaultPlatformContext(
                configuration, new NoOpMetrics(), CryptographyHolder.get(), Time.getCurrent());

        final WiringModel model = WiringModel.create(platformContext, Time.getCurrent(), ForkJoinPool.commonPool());

        final TaskScheduler<Integer> sequentialScheduler = model.schedulerBuilder("SequentialScheduler")
                .withType(TaskSchedulerType.SEQUENTIAL)
                .withUnhandledTaskCapacity(1)
                .build()
                .cast();
        final TaskScheduler<Void> sequentialThreadScheduler = model.schedulerBuilder("SequentialThreadScheduler")
                .withType(TaskSchedulerType.SEQUENTIAL_THREAD)
                .withUnhandledTaskCapacity(1)
                .build()
                .cast();
        final TaskScheduler<Void> directScheduler = model.schedulerBuilder("DirectScheduler")
                .withType(TaskSchedulerType.DIRECT)
                .build()
                .cast();
        final TaskScheduler<Void> directThreadsafeScheduler = model.schedulerBuilder("DirectThreadsafeScheduler")
                .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                .build()
                .cast();
        final TaskScheduler<Void> concurrentScheduler = model.schedulerBuilder("ConcurrentScheduler")
                .withType(TaskSchedulerType.CONCURRENT)
                .build()
                .cast();

        final String wireSubstitutionString = "wire substitution (for readability)";
        sequentialScheduler.getOutputWire().solderTo(sequentialThreadScheduler.buildInputWire(wireSubstitutionString));
        directScheduler.getOutputWire().solderTo(directThreadsafeScheduler.buildInputWire("wire with backpressure"));
        sequentialThreadScheduler
                .getOutputWire()
                .solderTo(directScheduler.buildInputWire("wire without backpressure"), SolderType.OFFER);

        final String diagramString = model.generateWiringDiagram(
                List.of(new ModelGroup(
                        "Arbitrary Grouping",
                        Set.of(
                                sequentialScheduler.getName(),
                                sequentialThreadScheduler.getName(),
                                directScheduler.getName(),
                                directThreadsafeScheduler.getName(),
                                concurrentScheduler.getName()),
                        false)),
                List.of(new ModelEdgeSubstitution(sequentialScheduler.getName(), wireSubstitutionString, "✩")),
                List.of(new ModelManualLink(
                        concurrentScheduler.getName(),
                        "manual diagram link (not actually a wire)",
                        sequentialScheduler.getName())),
                false);
        final String encodedDiagramString = Base64.getEncoder().encodeToString(diagramString.getBytes());

        final String editorUrl = "https://mermaid.ink/svg/" + encodedDiagramString + "?bgColor=e8e8e8";

        System.out.println(diagramString);
        System.out.println();
        System.out.println(editorUrl);
        return 0;
    }
}
