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

package com.swirlds.platform.gui.hashgraph;

/**
 * Options for drawing the {@code HashgraphPicture}
 */
public interface HashgraphPictureOptions {
    /**
     * @return should the picture freeze and not change
     */
    boolean isPictureFrozen();

    /**
     * @return should the hashgraph be expanded
     */
    boolean isExpanded();

    /**
     * @return should round created be written for every event
     */
    boolean writeRoundCreated();

    /**
     * @return should round received be written for every event
     */
    boolean writeRoundReceived();

    /**
     * @return should consensus order be written for every event
     */
    boolean writeConsensusOrder();

    /**
     * @return should consensus timestamp be written for every event
     */
    boolean writeConsensusTimeStamp();

    /**
     * @return should the generation be written for every event
     */
    boolean writeGeneration();

    /**
     * @return should the birth round be written for every event
     */
    boolean writeBirthRound();

    /**
     * @return should simple colors be used in the picture
     */
    boolean simpleColors();

    /**
     * @return the number of generations to display
     */
    int getNumGenerationsDisplay();

    /**
     * @return the first generation that should be displayed
     */
    long getStartGeneration();

    /**
     * @return should the latest events be displayed, ignores {@link #getStartGeneration()}
     */
    boolean displayLatestEvents();

    /**
     * When {@link #displayLatestEvents()} is true, this method will be called to notify which is the current starting
     * generation
     *
     * @param startGeneration
     * 		the first generation being displayed
     */
    void setStartGeneration(final long startGeneration);
}
