/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.gradle.spotless

import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep

/*
 Removes the old copyright statements which were incorrectly located between the package and import statements.
 These legacy copyright blocks also uses with an unexpected opening comment tag. This FormatterStep removes those
 comment blocks using a very conservative approach to avoid mutilating actual code.
 */
class StripOldLicenseFormatterStep {
    companion object {
        private const val NAME = "StripOldLicense"

        fun create(): FormatterStep {
            return FormatterStep.create(
                NAME,
                State(),
                State::toFormatter
            )
        }
    }

    private class State() : java.io.Serializable {
        companion object {
            private const val serialVersionUID = -113
        }

        fun toFormatter(): FormatterFunc {
            return FormatterFunc { unixStr ->
                val lines = unixStr.split('\n')
                val result = ArrayList<String>(lines.size)
                var inComment = false
                lines.forEach { s ->
                    if (!inComment && s.trim().startsWith("/*-")) {
                        inComment = true
                    } else if (inComment && s.trim().startsWith("*/")) {
                        inComment = false
                    } else if (!inComment) {
                        result.add(s)
                    }
                }

                val finalStr = result.joinToString("\n")
                finalStr
            }
        }
    }
}
