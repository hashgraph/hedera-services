/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.utils.exception;

/**
 * An exception that thrown if unknown HederaFunctionality is found.
 *
 */
public class UnknownHederaFunctionality extends Exception {
    /** this is for backward compatibility as some current code also uses this exception*/
    public UnknownHederaFunctionality() {
        super();
    }

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param errMessage, the detail error message.
     */
    public UnknownHederaFunctionality(String errMessage) {
        super(errMessage);
    }
}
