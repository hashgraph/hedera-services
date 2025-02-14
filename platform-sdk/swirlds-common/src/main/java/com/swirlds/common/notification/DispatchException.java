// SPDX-License-Identifier: Apache-2.0
package com.swirlds.common.notification;

import com.swirlds.common.exceptions.PlatformException;
import com.swirlds.logging.legacy.LogMarker;

public class DispatchException extends PlatformException {

    public DispatchException(final String message) {
        super(message, LogMarker.EXCEPTION);
    }

    public DispatchException(final String message, final Throwable cause) {
        super(message, cause, LogMarker.EXCEPTION);
    }

    public DispatchException(final Throwable cause) {
        super(cause, LogMarker.EXCEPTION);
    }
}
