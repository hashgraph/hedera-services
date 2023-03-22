/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import com.swirlds.platform.internal.SubSetting;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateManager;
import java.time.Duration;

/**
 * Settings that control the {@link SignedStateManager} and {@link SignedStateFileManager} behaviors.
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public class StateSettings extends SubSetting {

    /**
     * The directory where states are saved. This is relative to the current working directory, unless
     * the provided path begins with "/", in which case it will be interpreted as an absolute path.
     */
    public String savedStateDirectory = "data/saved";

    /**
     * If true, clean out all data in the {@link #savedStateDirectory} except for the previously saved state.
     */
    public boolean cleanSavedStateDirectory = false;

    /**
     * The number of states permitted to sit in the signed state file manager's queue of states being written.
     * If this queue backs up then some states may not be written to disk.
     */
    public int stateSavingQueueSize = 20;

    /**
     * The frequency of writes of a state to disk every this many seconds (0 to never write).
     */
    public int saveStatePeriod = 0;

    /**
     * Keep at least this many of the old complete signed states on disk. This should be at least 2 so that
     * we don't delete an old state while a new one is in the process of writing to disk. set to 0 to not
     * keep any states to disk.
     */
    public int signedStateDisk = 3;

    /**
     * The maximum number of rounds that a state will be kept in memory while waiting for it to gather
     * enough signatures. If a state becomes fully signed prior to reaching this age it may be removed from memory.
     */
    public int roundsToKeepForSigning = 26;

    /**
     * If true, then save the state to disk when there is a fatal exception.
     */
    public boolean dumpStateOnFatal = true;

    /**
     * The minimum time, in seconds, that must transpire after dumping a state before another state dump is permitted.
     */
    public long secondsBetweenISSDumps = Duration.ofHours(6).toSeconds();

    /**
     * Dump the state when there is an ISS even if this node is not in an ISS state.
     * This feature is for debugging purposes and should not be active in production systems.
     */
    public boolean dumpStateOnAnyISS = false;

    /**
     * <p>
     * Halt this node whenever any ISS in the network is detected.
     * A halt causes the node to stop doing work, but does not shut
     * down the JVM.
     * </p>
     *
     * <p>
     * This feature is for debugging purposes only. Enabling this feature in
     * production environments enables a very simple denial of service attack
     * on the network.
     * </p>
     */
    public boolean haltOnAnyIss = false;

    /**
     * The minimum time that must pass between log messages about ISS events. If ISS events happen
     * with a higher frequency then they are squelched.
     */
    public int secondsBetweenIssLogs = (int) Duration.ofMinutes(5).toSeconds();

    /**
     * If true, then attempt to recover automatically when a self ISS is detected.
     */
    public boolean automatedSelfIssRecovery = false;

    /**
     * If true, then halt this node if a catastrophic ISS is detected. A halt causes the node to stop doing work,
     * but does not shut down the JVM.
     */
    public boolean haltOnCatastrophicIss = false;

    /**
     * If true then a single background thread is used to do validation of signed state hashes. Validation is on
     * a best effort basis. If it takes too long to validate a state then new states will be skipped.
     */
    public static boolean backgroundHashChecking = false;

    /**
     * When logging debug information about the hashes in a merkle tree, do not display hash information
     * for nodes deeper than this.
     */
    public static int debugHashDepth = 5;

    /**
     * If there are problems with state lifecycle then write errors to the log at most once per this period of time.
     */
    public int stateDeletionErrorLogFrequencySeconds = 60;

    /**
     * When enabled, hashes for the nodes are logged per round.
     */
    public boolean enableHashStreamLogging = true; // NOSONAR: Value is modified and updated by reflection.

    /**
     * If true, then enable extra debug code that tracks signed states. Very useful for debugging state leaks.
     * This debug code is relatively expensive (it takes and stores stack traces when operations are
     * performed on signed state objects).
     */
    public boolean signedStateSentinelEnabled = true;

    /**
     * Ignored if {@link #signedStateSentinelEnabled} is not true. The age of a signed state, in seconds, which is
     * considered to be suspicious. Suspicious states cause a large amount of data to be logged that helps to
     * debug the potential state leak.
     */
    public Duration suspiciousSignedStateAge = Duration.ofMinutes(5);

    /**
     * It's possible to receive state signatures before it's time to process the round signed by the signature.
     * This is the maximum number of rounds, in the future, for which a node will accept a state signature.
     */
    public int maxAgeOfFutureStateSignatures = 1_000;

    public StateSettings() {}

    /**
     * Get the minimum amount of time between when errors about state deletion are logged.
     */
    public int getStateDeletionErrorLogFrequencySeconds() {
        return stateDeletionErrorLogFrequencySeconds;
    }

    /**
     * getter for the frequency of writes of a state to disk
     *
     * @return the frequency of writes of a state to disk
     */
    public int getSaveStatePeriod() {
        return saveStatePeriod;
    }

    /**
     * getter for the number of old complete signed states to be kept on disk
     *
     * @return the number of old complete signed states to be kept on disk
     */
    public int getSignedStateDisk() {
        return signedStateDisk;
    }

    /**
     * When logging debug information about the hashes in a merkle tree, do not display hash information
     * for nodes deeper than this.
     *
     * @return the maximum depth when displaying debug information about the hash of the state
     */
    public static int getDebugHashDepth() {
        return debugHashDepth;
    }
}
