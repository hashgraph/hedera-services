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

package com.swirlds.common.exceptions;

import com.swirlds.logging.LogMarker;

public class PlatformException extends RuntimeException {

    private final LogMarker logMarker;

    public PlatformException(final LogMarker logMarker) {
        this.logMarker = logMarker;
    }

    public PlatformException(final String message, final LogMarker logMarker) {
        super(message);
        this.logMarker = logMarker;
    }

    public PlatformException(final String message, final Throwable cause, final LogMarker logMarker) {
        super(message, cause);
        this.logMarker = logMarker;
    }

    public PlatformException(final Throwable cause, final LogMarker logMarker) {
        super(cause);
        this.logMarker = logMarker;
    }

    public LogMarker getLogMarkerInfo() {
        return logMarker;
    }
}
