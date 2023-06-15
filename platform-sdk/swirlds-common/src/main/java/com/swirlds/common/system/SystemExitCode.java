/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system;

public interface SystemExitCode {

    static final SystemExitCode NO_ERROR = new SystemExitCode() {
        @Override
        public int getExitCode() {
            return 0;
        }

        @Override
        public boolean isError() {
            return false;
        }

        @Override
        public String name() {
            return "NO_ERROR";
        }
    };

    /**
     * Returns the exit code
     *
     * @return
     */
    int getExitCode();

    /**
     * Returns true if the exit code is an error
     *
     * @return true if the exit code is an error
     */
    boolean isError();

    /**
     * Returns the name of the exit code
     *
     * @return the name of the exit code
     */
    String name();
}
