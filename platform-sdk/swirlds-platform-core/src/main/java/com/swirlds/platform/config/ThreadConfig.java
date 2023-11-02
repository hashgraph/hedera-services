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

package com.swirlds.platform.config;

import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

/**
 * Thread related config
 *
 * @param logStackTracePauseDuration If a thread takes longer than this duration to {@link StoppableThread#pause()}, log
 *                                   a stack trace for debugging purposes. A value of {@link Duration#ZERO} means never
 *                                   log.
 * @param threadPrioritySync         priority for threads that sync (in SyncCaller, SyncListener, SyncServer)
 * @param threadPriorityNonSync      priority for threads that don't sync (all but SyncCaller, SyncListener,SyncServer)
 * @param threadDumpPeriodMs         period of generating thread dump file in the unit of milliseconds
 * @param threadDumpLogDir           thread dump files will be generated in this directory
 * @param jvmAnchor                  if true then create a non-daemon thread that will keep the JVM alive
 */
@ConfigData("thread")
public record ThreadConfig(
        @ConfigProperty(defaultValue = "5s") Duration logStackTracePauseDuration,
        @ConfigProperty(defaultValue = "5") int threadPrioritySync,
        @ConfigProperty(defaultValue = "5") int threadPriorityNonSync,
        @ConfigProperty(defaultValue = "0") long threadDumpPeriodMs,
        @ConfigProperty(defaultValue = "data/threadDump") String threadDumpLogDir,
        @ConfigProperty(defaultValue = "true") boolean jvmAnchor) {}
