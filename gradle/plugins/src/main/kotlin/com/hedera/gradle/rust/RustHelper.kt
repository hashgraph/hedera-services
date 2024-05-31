import com.moandjiezana.toml.Toml
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.tasks.StopExecutionException
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class RustHelper {
    companion object {
        private const val LINUX_MAC_EXECUTABLE: String = "cargo"
        private const val WINDOWS_EXECUTEABLE: String = "cargo.exe"

        @JvmStatic
        fun selectTriplet(): RustTriplet {
            for (it in RustTriplet.values()) {
                if (Os.isFamily(it.operatingSystem) && Os.isArch(it.architecture)) {
                    if (it.operatingSystem.equals(Os.FAMILY_UNIX) && Os.isFamily(Os.FAMILY_MAC)) {
                        continue
                    }

                    return it
                }
            }

            val OS_NAME = System.getProperty("os.name").lowercase();
            val OS_ARCH = System.getProperty("os.arch").lowercase()
            throw StopExecutionException("The OS family:" + OS_NAME +  " or system architecture: " + OS_ARCH+ "is not supported." )
        }

        @JvmStatic
        fun rustCommand(isWin: Boolean):String {
            val command: String = if (isWin) WINDOWS_EXECUTEABLE else LINUX_MAC_EXECUTABLE
            return findProgramPath(command) //TODO: Maybe there is another way?
        }


        @JvmStatic
        fun readCargoLibraryName(manifest: File): String {
            val inputStream = Files.newInputStream(manifest.toPath())
            inputStream.use {
                val toml: Toml = Toml().read(it)
                return toml.getString("lib.name")
            }
        }

        @JvmStatic
        fun predictArtifactPath( libraryName: String): File {
            val triplet = selectTriplet()
            val cargoBuildPath = Path.of("target", triplet.identifier, "release")
            val artifactName = "${triplet.filePrefix}${libraryName}.${triplet.fileExtension}"

            return cargoBuildPath.resolve(artifactName).toFile()
        }

        @JvmStatic
        fun findProgramPath(program: String): String {
            val whichCommand = if (Os.isFamily(Os.FAMILY_WINDOWS)) "where" else "which"
            return try {
                val process = ProcessBuilder(whichCommand, program).start()
                val result = process.inputStream.bufferedReader().readText().trim()
                if (process.waitFor() == 0 && result.isNotEmpty()) {
                    result
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }
    }
}
