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

package com.swirlds.common.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Config that control the SignedStateManager and SignedStateFileManager behaviors.
 *
 * @param savedStateDirectory           The directory where states are saved. This is relative to the current working
 *                                      directory, unless the provided path begins with "/", in which case it will be
 *                                      interpreted as an absolute path.
 * @param mainClassNameOverride         Typically, the fully qualified name of the application class implementing
 *                                      {@link com.swirlds.common.system.SwirldMain SwirldMain} is used as a directory
 *                                      name when saving signed states. If this property is not the empty string then it
 *                                      overrides the main class name for signed states.
 * @param stateSavingQueueSize          The number of states permitted to sit in the signed state file manager's queue
 *                                      of states being written. If this queue backs up then some states may not be
 *                                      written to disk.
 * @param saveStatePeriod               The frequency of writes of a state to disk every this many seconds (0 to never
 *                                      write).
 * @param signedStateDisk               Keep at least this many of the old complete signed states on disk. This should
 *                                      be at least 2 so that  we don't delete an old state while a new one is in the
 *                                      process of writing to disk. set to 0 to not keep any states to disk.
 * @param dumpStateOnAnyISS             If true, save the state to disk when an ISS is detected. May negatively affect
 *                                      the performance of the node where the ISS occurs. This feature is for debugging
 *                                      purposes and should not be active in production systems.
 * @param dumpStateOnFatal              If true, then save the state to disk when there is a fatal exception.
 * @param haltOnAnyIss                  <p>
 *                                      Halt this node whenever any ISS in the network is detected. A halt causes the
 *                                      node to stop doing work, but does not shut down the JVM.
 *                                      </p>
 *
 *                                      <p>
 *                                      This feature is for debugging purposes only. Enabling this feature in production
 *                                      environments enables a very simple denial of service attack on the network.
 *                                      </p>
 * @param automatedSelfIssRecovery      If true, then attempt to recover automatically when a self ISS is detected.
 * @param haltOnCatastrophicIss         If true, then halt this node if a catastrophic ISS is detected. A halt causes
 *                                      the node to stop doing work, but does not shut down the JVM.
 * @param secondsBetweenISSDumps        If one ISS is detected, it is likely that others will be detected shortly
 *                                      afterward. Specify the minimum time, in seconds, that must transpire after
 *                                      dumping a state before another state dump is permitted. Ignored if
 *                                      dumpStateOnISS is false.
 * @param secondsBetweenIssLogs         The minimum time that must pass between log messages about ISS events. If ISS
 *                                      events happen with a higher frequency then they are squelched.
 * @param enableHashStreamLogging       When enabled, hashes for the nodes are logged per round.
 * @param debugHashDepth                When logging debug information about the hashes in a merkle tree, do not display
 *                                      hash information for nodes deeper than this.
 * @param maxAgeOfFutureStateSignatures It's possible to receive state signatures before it's time to process the round
 *                                      signed by the signature. This is the maximum number of rounds, in the future,
 *                                      for which a node will accept a state signature.
 * @param roundsToKeepForSigning        The maximum number of rounds that a state will be kept in memory while waiting
 *                                      for it to gather enough signatures. If a state becomes fully signed prior to
 *                                      reaching this age it may be removed from memory.
 * @param roundsToKeepAfterSigning      The number of rounds to keep states after they have been signed and after a
 *                                      newer state has become fully signed. If set to 0 then each state becomes garbage
 *                                      collection eligible as soon as it is not the most recently signed state.
 * @param suspiciousSignedStateAge      The age of a signed state which is considered to be suspicious. Suspicious
 *                                      states cause a large amount of data to be logged that helps to debug the
 *                                      potential state leak.
 * @param stateHistoryEnabled           If true, then a history of operations that modify the signed state reference
 *                                      count are kept for debugging purposes.
 * @param debugStackTracesEnabled       if true and stateHistoryEnabled is true, then stack traces are captured each
 *                                      time a signed state reference count is changed, and logged if a signed state
 *                                      reference count bug is detected.
 * @param emergencyStateFileName        The name of the file that contains the emergency state.
 * @param signedStateFreq               hash and sign a state every signedStateFreq rounds. 1 means that a state will be
 *                                      signed every round, 2 means every other round, and so on. If the value is 0 or
 *                                      less, no states will be signed
 * @param deleteInvalidStateFiles       At startup time, if a state can not be deserialized without errors, should we
 *                                      delete that state from disk and try another? If true then states that can't be
 *                                      parsed are deleted. If false then a node will crash if it can't parse a state
 *                                      file. Possibly useful if a node (or nodes) have corrupted state files. Be very
 *                                      careful enabling this network wide. If this is enabled and all states on disk
 *                                      have deserialization bugs, then all nodes will delete all state copies and the
 *                                      network will restart from genesis.
 * @param validateInitialState          If false then do not do ISS validation on the state loaded from disk at startup.
 *                                      This should always be enabled in production environments. Disabling initial
 *                                      state validation is intended to be a test-only feature.
 */
@ConfigData("state")
public record StateConfig(
        @ConfigProperty(defaultValue = "data/saved") Path savedStateDirectory,
        @ConfigProperty(defaultValue = "") String mainClassNameOverride,
        @ConfigProperty(defaultValue = "20") int stateSavingQueueSize,
        @ConfigProperty(defaultValue = "900") int saveStatePeriod,
        @ConfigProperty(defaultValue = "5") int signedStateDisk,
        @ConfigProperty(defaultValue = "false") boolean dumpStateOnAnyISS,
        @ConfigProperty(defaultValue = "true") boolean dumpStateOnFatal,
        @ConfigProperty(defaultValue = "false") boolean haltOnAnyIss,
        @ConfigProperty(defaultValue = "false") boolean automatedSelfIssRecovery,
        @ConfigProperty(defaultValue = "false") boolean haltOnCatastrophicIss,
        @ConfigProperty(defaultValue = "21600") long secondsBetweenISSDumps,
        @ConfigProperty(defaultValue = "300") long secondsBetweenIssLogs,
        @ConfigProperty(defaultValue = "true") boolean enableHashStreamLogging,
        @ConfigProperty(defaultValue = "5") int debugHashDepth,
        @ConfigProperty(defaultValue = "1000") int maxAgeOfFutureStateSignatures,
        @ConfigProperty(defaultValue = "26") int roundsToKeepForSigning,
        @ConfigProperty(defaultValue = "0") int roundsToKeepAfterSigning,
        @ConfigProperty(defaultValue = "5m") Duration suspiciousSignedStateAge,
        @ConfigProperty(defaultValue = "false") boolean stateHistoryEnabled,
        @ConfigProperty(defaultValue = "false") boolean debugStackTracesEnabled,
        @ConfigProperty(defaultValue = "emergencyRecovery.yaml") String emergencyStateFileName,
        @ConfigProperty(defaultValue = "1") int signedStateFreq,
        @ConfigProperty(defaultValue = "false") boolean deleteInvalidStateFiles,
        @ConfigProperty(defaultValue = "true") boolean validateInitialState) {

    /**
     * Get the main class name that should be used for signed states.
     *
     * @param defaultMainClassName the default main class name derived from the
     *                             {@link com.swirlds.common.system.SwirldMain SwirldMain} name.
     * @return the main class name that should be used for signed states
     */
    public String getMainClassName(final String defaultMainClassName) {
        return mainClassNameOverride.isEmpty() ? defaultMainClassName : mainClassNameOverride;
    }
}
