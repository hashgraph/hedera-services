import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep
import java.util.regex.Pattern

class StripOldLicenseFormatterStep {
    companion object {
        private const val NAME = "StripOldLicense"
        private const val REGEX = "\\/\\*-.+Copyright \\(C\\).+\\*\\/"

        fun create(): FormatterStep {
            return FormatterStep.create(
                NAME,
                State(Regex(REGEX, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))),
                State::toFormatter
            )
        }
    }

    private class State(val pattern: Regex) : java.io.Serializable {
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
