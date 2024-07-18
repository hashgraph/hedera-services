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

plugins { id("com.diffplug.spotless") }

spotless {
    // Disable the automatic application of Spotless to all source sets when the check task is run.
    isEnforceCheck = false

    // optional: limit format enforcement to just the files changed by this feature branch
    ratchetFrom("origin/develop")

    flexmark {
        target("**/*.md", ".gitignore")
        flexmark()
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
}
