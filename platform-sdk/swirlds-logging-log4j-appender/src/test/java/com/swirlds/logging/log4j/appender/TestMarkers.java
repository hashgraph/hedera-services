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

package com.swirlds.logging.log4j.appender;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class TestMarkers {
    public static final Marker grantMarker;
    public static final Marker parentMarker;
    public static final Marker childMarker;

    public static final String GRANT = "GRANT";

    public static final String PARENT = "PARENT";

    public static final String CHILD = "CHILD";

    static {
        grantMarker = MarkerManager.getMarker(GRANT);
        parentMarker = MarkerManager.getMarker(PARENT).addParents(grantMarker);
        childMarker = MarkerManager.getMarker(CHILD).addParents(parentMarker);
    }
}
