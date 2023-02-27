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

package com.swirlds.common.utility;

import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

/**
 * Combine a list of {@link Clearable} instances into a single one with logging. The combined instance will execute clear
 * on each of the provided instances sequentially, while logging before each clear
 */
public class LoggingClearables implements Clearable {
    private static final Logger logger = LogManager.getLogger(LoggingClearables.class);
    private final Marker logMarker;
    private final List<Pair<Clearable, String>> list;

    /**
     * @param logMarker
     * 		the log marker to use for logging
     * @param pairs
     * 		a list of pairs, each clearable is paired up with a string name used for logging
     */
    public LoggingClearables(final Marker logMarker, final List<Pair<Clearable, String>> pairs) {
        this.logMarker = logMarker;
        this.list = pairs;
    }

    @Override
    public void clear() {
        for (final Pair<Clearable, String> pair : list) {
            logger.info(logMarker, "about to clear {}", pair::getRight);
            pair.getLeft().clear();
        }
    }
}
