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

package com.swirlds.platform.util;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The result of a shell command.
 *
 * @param exitCode the exit code of the command
 * @param out      text written to stdout
 * @param error    text written to stderr
 */
public record CommandResult(int exitCode, @NonNull String out, @NonNull String error) {

    /**
     * Returns true if the command exited with a zero exit code.
     */
    public boolean isSuccessful() {
        return exitCode == 0;
    }
}
