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

/**
 * Adds self-correcting behavior as spotless step which properly removes the comments which causes the
 * google-java-formatter plugin to rupture (eg: \/\*-).
 */
class RepairDashedCommentsFormatterStep {
    companion object {
        private const val NAME = "RepairDashedComments"
        private const val OPENING_COMMENT_REGEX = "/\\*-+"
        private const val CLOSING_COMMENT_REGEX = "-+\\*/"

        fun create(): FormatterStep {
            val openingCommentRegex = Regex(OPENING_COMMENT_REGEX, setOf(RegexOption.IGNORE_CASE))
            val closingCommentRegex = Regex(CLOSING_COMMENT_REGEX, setOf(RegexOption.IGNORE_CASE))
            return FormatterStep.create(
                NAME,
                State(openingCommentRegex, closingCommentRegex),
                State::toFormatter
            )
        }
    }

    private class State(val openingCommentRegex: Regex, val closingCommentRegex: Regex) :
        java.io.Serializable {
        companion object {
            private const val serialVersionUID = -113
        }


        fun toFormatter(): FormatterFunc {
            return FormatterFunc { unixStr ->
                val lines = unixStr.split('\n')
                val result = ArrayList<String>(lines.size)
                var inLicenseBlock = false

                lines.forEach { s ->
                    if (!inLicenseBlock && s.trim().equals("/*-")) {
                        inLicenseBlock = true
                    } else if (inLicenseBlock && s.trim().equals("*/")) {
                        inLicenseBlock = false
                    }

                    if (inLicenseBlock) {
                        result.add(s)
                    } else {
                        result.add(
                            s.replace(openingCommentRegex, "/*").replace(closingCommentRegex, "*/")
                        )
                    }
                }

                val finalStr = result.joinToString("\n")
                finalStr
            }
        }
    }
}
