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
