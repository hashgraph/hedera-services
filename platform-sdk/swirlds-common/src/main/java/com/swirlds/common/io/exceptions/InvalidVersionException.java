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

package com.swirlds.common.io.exceptions;

import com.swirlds.common.io.SerializableDet;

/**
 * Exception that is caused when illegal version is read from the stream
 */
public class InvalidVersionException extends IllegalArgumentException {
    public InvalidVersionException(final int expectedVersion, final int version) {
        super(String.format("Illegal version %d was read from the stream. Expected %d", version, expectedVersion));
    }

    public InvalidVersionException(final int minimumVersion, final int maximumVersion, final int version) {
        super(String.format(
                "Illegal version %d was read from the stream. Expected version in the range %d - %d",
                version, minimumVersion, maximumVersion));
    }

    public InvalidVersionException(final int version, SerializableDet object) {
        super(String.format(
                "Illegal version %d was read from the stream for %s (class ID %d(0x%08X)). Expected version in the "
                        + "range %d - %d",
                version,
                object.getClass(),
                object.getClassId(),
                object.getClassId(),
                object.getMinimumSupportedVersion(),
                object.getVersion()));
    }
}
