/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.preconsensus;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.validation.annotation.Min;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Configuration for preconsensus event storage.
 *
 * @param writeQueueCapacity                              the queue capacity for preconsensus events waiting to be
 *                                                        written to disk
 * @param minimumRetentionPeriod                          the minimum amount of time that preconsensus events should be
 *                                                        stored on disk. At a minimum, should exceed the length of time
 *                                                        between state saving.
 * @param preferredFileSizeMegabytes                      the preferred file size for preconsensus event files. Not a
 *                                                        strong guarantee on file size, more of a suggestion.
 * @param bootstrapGenerationalSpan                       when first starting up a preconsensus event file manager, the
 *                                                        running average for the generational utilization will not have
 *                                                        any values in it. Use this value until the running average
 *                                                        represents real data. Once the running average for the
 *                                                        generational utilization becomes available, then this value is
 *                                                        ignored.
 * @param generationalUtilizationSpanRunningAverageLength the preconsensus event stream tracks the running average of
 *                                                        the generational utilization in event stream files. It uses
 *                                                        this running average in a heuristic when deciding the maximum
 *                                                        generation permitted in a new file. This value controls the
 *                                                        number of recent files that are considered when computing the
 *                                                        running average.
 * @param bootstrapGenerationalSpanOverlapFactor          when choosing the generation span for a new file during the
 *                                                        bootstrapping phase, multiply the running average of previous
 *                                                        file generational utilization by this factor. Choosing a value
 *                                                        too large will cause unnecessary un-utilized generational span
 *                                                        if the node crashes. Choosing a value too small may case event
 *                                                        files to be smaller than the preferred size. Should be larger
 *                                                        than the non-boostrap span overlap factor to allow for faster
 *                                                        convergence on a sane file size at startup time.
 * @param generationalSpanOverlapFactor                   when choosing the generation span for a new file, multiply the
 *                                                        running average of previous file generational utilization by
 *                                                        this factor. Choosing a value too large will cause unnecessary
 *                                                        un-utilized generational span if the node crashes. Choosing a
 *                                                        value too small may case event files to be smaller than the
 *                                                        preferred size.
 * @param minimumGenerationalCapacity                     when creating a new file, make sure it at least has the
 *                                                        capacity for this many generations after the generation of the
 *                                                        first event in the file. This is puts a sane "floor" on the
 *                                                        generational span heuristic, so that we never attempt to open
 *                                                        a file that doesn't have capacity for events, regardless of
 *                                                        the required generational span to do so. If properly
 *                                                        configured and under steady state operation, this capacity is
 *                                                        unlikely to be a limiting factor on the generational span of
 *                                                        files.
 * @param permitGaps                                      if false (default) then throw an exception if we attempt to
 *                                                        load preconsensus events and notice gaps in the file sequence.
 *                                                        This is only possible if a preconsensus event file has been
 *                                                        deleted out of band. This setting is present only to allow
 *                                                        emergency manual action. In general, allowing gaps is likely
 *                                                        to either lead to ISSes or, more likely, cause events to be
 *                                                        added to the hashgraph without their parents being added. Or
 *                                                        Both. Use this with caution.
 * @param databaseDirectory                               the directory where preconsensus events will be stored,
 *                                                        relative to
 *                                                        {@link com.swirlds.common.config.StateConfig#savedStateDirectory()}.
 * @param enableStorage                                   if true, then stream preconsensus events to files on disk. If
 *                                                        this is disabled then a network wide crash (perhaps due to a
 *                                                        bug) can cause transactions that previously reached consensus
 *                                                        to be "forgotten" and effectively rolled back.
 * @param enableReplay                                    if true, then replay preconsensus events at boot time after
 *                                                        loading a signed state. If this is disabled then a network
 *                                                        wide crash (perhaps due to a bug) can cause transactions that
 *                                                        previously reached consensus to be "forgotten" and effectively
 *                                                        rolled back.
 * @param replayQueueSize                                 the size of the queue used for holding preconsensus events
 *                                                        that are waiting to be replayed
 * @param replayHashPoolSize                              the number of threads used for hashing events during replay
 * @param clearOnSoftwareUpgrade                          if true, then delete all preconsensus event files when a
 *                                                        software upgrade is taking place.
 */
@ConfigData("event.preconsensus")
public record PreconsensusEventStreamConfig(
        @ConfigProperty(defaultValue = "1000") int writeQueueCapacity,
        @ConfigProperty(defaultValue = "1h") Duration minimumRetentionPeriod,
        @ConfigProperty(defaultValue = "10") int preferredFileSizeMegabytes,
        @ConfigProperty(defaultValue = "50") int bootstrapGenerationalSpan,
        @ConfigProperty(defaultValue = "5") int generationalUtilizationSpanRunningAverageLength,
        @Min(1) @ConfigProperty(defaultValue = "10") double bootstrapGenerationalSpanOverlapFactor,
        @Min(1) @ConfigProperty(defaultValue = "1.2") double generationalSpanOverlapFactor,
        @ConfigProperty(defaultValue = "5") int minimumGenerationalCapacity,
        @ConfigProperty(defaultValue = "false") boolean permitGaps,
        @ConfigProperty(defaultValue = "preconsensus-events") Path databaseDirectory,
        @ConfigProperty(defaultValue = "true") boolean enableStorage,
        @ConfigProperty(defaultValue = "true") boolean enableReplay,
        @ConfigProperty(defaultValue = "1024") int replayQueueSize,
        @ConfigProperty(defaultValue = "8") int replayHashPoolSize,
        @ConfigProperty(defaultValue = "false") boolean clearOnSoftwareUpgrade) {}
