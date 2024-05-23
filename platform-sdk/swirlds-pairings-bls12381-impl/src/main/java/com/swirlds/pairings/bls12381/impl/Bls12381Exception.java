/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.pairings.bls12381.impl;

import java.util.HashMap;
import java.util.Map;

/** Exception thrown when the BLS12_381 library encounters an unexpected error */
public class Bls12381Exception extends RuntimeException {
    /** A static map between error code and error string */
    private static final Map<Integer, String> errorCodeMap;

    static {
        errorCodeMap = new HashMap<>();
        errorCodeMap.put(1, "JNI");
        errorCodeMap.put(2, "TryFromSlice");
        errorCodeMap.put(3, "TryInto");
        errorCodeMap.put(4, "InputLength");
        errorCodeMap.put(5, "OutputLength");
    }

    /**
     * Constructor
     *
     * @param functionName the name of the library function where the error occurred
     * @param errorCode the error code that was returned
     */
    public Bls12381Exception(final String functionName, final int errorCode) {
        super(functionName
                + " returned error ["
                + errorCode
                + ": "
                + errorCodeMap.getOrDefault(errorCode, "unknown")
                + "]");
    }
}
