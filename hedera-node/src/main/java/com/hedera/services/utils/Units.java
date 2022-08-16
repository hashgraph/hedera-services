/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils;

public final class Units {
    private Units() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /** Conversion factor from hbar to tinybar */
    public static final long HBARS_TO_TINYBARS = 100_000_000L;

    /** Conversion factor from minutes to seconds */
    public static final long MINUTES_TO_SECONDS = 60L;

    /** Conversion factor from minutes to milliseconds */
    public static final long MINUTES_TO_MILLISECONDS = 60_000L;

    /** Minimum size of a consensus time increment in nanoseconds */
    public static final long MIN_TRANS_TIMESTAMP_INCR_NANOS = 1_000;
}
