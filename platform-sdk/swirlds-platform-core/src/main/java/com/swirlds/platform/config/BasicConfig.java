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

package com.swirlds.platform.config;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Basic configuration data record. This record contains all general config properties that can not be defined for a
 * specific subsystem. The record is based on the definition of config data objects as described in {@link ConfigData}.
 *
 * <p>
 * Do not add new settings to this record unless you have a very good reason. New settings should go into config records
 * with a prefix defined by a {@link ConfigData} tag. Adding settings to this record pollutes the top level namespace.
 *
 * @param numConnections               number of connections maintained by each member (syncs happen on random
 *                                     connections from that set
 * @param loadKeysFromPfxFiles         When enabled, the platform will try to load node keys from .pfx files located in
 *                                     the {@code PathsConfig.keysDirPath}. If even a single key is missing, the
 *                                     platform will warn and exit. If disabled, the platform will generate keys
 *                                     deterministically.
 * @param jvmPauseDetectorSleepMs      period of JVMPauseDetectorThread sleeping in the unit of milliseconds
 * @param jvmPauseReportMs             log an error when JVMPauseDetectorThread detect a pause greater than this many
 *                                     milliseconds all peers
 * @param hangingThreadDuration        the length of time a gossip thread is allowed to wait when it is asked to
 *                                     shutdown. If a gossip thread takes longer than this period to shut down, then an
 *                                     error message is written to the log.
 * @param emergencyRecoveryFileLoadDir The path to look for an emergency recovery file on node start. If a file is
 *                                     present in this directory at startup, emergency recovery will begin.
 * @param genesisFreezeTime            If this node starts from genesis, this value is used as the freeze time. This
 *                                     feature is deprecated and planned for removal in a future platform version.
 */
@ConfigData
public record BasicConfig(
        @ConfigProperty(defaultValue = "1000") int numConnections,
        @ConfigProperty(defaultValue = "true") boolean loadKeysFromPfxFiles,
        @ConfigProperty(defaultValue = "1000") int jvmPauseDetectorSleepMs,
        @ConfigProperty(defaultValue = "1000") int jvmPauseReportMs,
        @ConfigProperty(defaultValue = "60s") Duration hangingThreadDuration,
        @ConfigProperty(defaultValue = "data/saved") String emergencyRecoveryFileLoadDir,
        @ConfigProperty(defaultValue = "0") long genesisFreezeTime) {

    /**
     * @return Absolute path to the emergency recovery file load directory.
     */
    public Path getEmergencyRecoveryFileLoadDir() {
        return getAbsolutePath().resolve(emergencyRecoveryFileLoadDir());
    }
}
