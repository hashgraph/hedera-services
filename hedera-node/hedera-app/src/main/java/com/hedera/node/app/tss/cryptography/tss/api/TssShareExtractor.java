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

package com.hedera.node.app.tss.cryptography.tss.api;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * This class, from a threshold minimum number of valid {@link TssMessage}s, allows to extract:
 * <ul>
 *      <li>all private shares that belongs to this participant
 *      <li>all public shares for all the participants in the scheme.
 * </ul>
 * <p>
 * It is the responsibility of the caller to ensure:
 * <ul>
 *      <li> that the list of processed {@link TssMessage} messages were previously validated
 *      <li> the number of messages meets the required threshold.
 * </ul>
 * The behaviour if those two conditions is not met is not defined.
 */
public interface TssShareExtractor {

    /**
     * Sets the mode of execution to async.
     * The implementation will make best attempt to assign the processing of each share to a different thread.
     * FUTURE-WORK: set the async mode
     * @param executorService the executor service that can be used to concurrently extract the shares.
     * @return this
     */
    @NonNull
    TssShareExtractor async(@NonNull ExecutorService executorService);

    /**
     * Returns the progress of the process
     * @return the progress of the process
     */
    @NonNull
    TssShareExtractionStatus status();

    /**
     * Compute all public shares for all the participants in the scheme and all private shares that belongs to this participant
     *  from a threshold minimum number of {@link TssMessage}s.
     * It is the responsibility of the caller to ensure:
     *   a) that the list of processed {@link TssMessage} messages are valid
     *   b) the number of messages meets the required threshold.
     *<p>
     * The result of processing the messages is stored internally.
     *<p>
     * FUTURE-WORK: if the async mode is set, this method returns right after being called.
     *
     * @implNote This is a computational intensive process, as it generates a data-structure per each assigned share of the protocol.
     * {@code O(threshold^2Xowned-shares) + O(threshold^2Xtotal-shares)}
     * @param privateInfo Info that is private to the participant extracting its own shares.
     * @return this
     */
    @NonNull
    TssShareExtractor extract(@NonNull TssParticipantPrivateInfo privateInfo);

    /**
     * Returns all private shares that belongs to this participant from a threshold minimum number of {@link TssMessage}s.
     * FUTURE-WORK: if the async mode is set, this method blocks until the privates shares are processed.
     * @return a sorted by {@link TssPrivateShare#shareId()} list of {@link TssPrivateShare} owned by the participant.
     * @param privateInfo the private information of the participant extracting the shares
     * @throws IllegalStateException if there aren't enough messages to meet the threshold
     */
    @NonNull
    List<TssPrivateShare> ownedPrivateShares(@NonNull TssParticipantPrivateInfo privateInfo);

    /**
     * Returns all public shares for all the participants in the scheme.
     * FUTURE-WORK: if the async mode is set, this method blocks until the public shares are processed.
     * @return a sorted by {@link TssPublicShare#shareId()} list of {@link TssPublicShare}.
     * @throws IllegalStateException if there aren't enough messages to meet the threshold
     */
    @NonNull
    List<TssPublicShare> allPublicShares();

    /**
     * Represents the progress of the process.
     */
    interface TssShareExtractionStatus {
        /**
         * If the share extraction was completed
         * @return If the share extraction was completed
         */
        boolean isCompleted();

        /**
         * a number between 0 and 100
         * @return the % of completed
         */
        byte percentComplete();

        /**
         * The total number of milliseconds the process have been running
         * @return the total number of milliseconds the process have been running
         */
        long elapsedTimeMs();

        /**
         * The estimated remaining number of milliseconds the process needs for completing the extraction
         * @return the estimated remaining number of milliseconds the process needs for completing the extraction
         */
        long approximateRemainingTimeMs();
    }
}
