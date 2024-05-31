import org.apache.tools.ant.taskdefs.condition.Os

enum class RustTriplet(val identifier: String, val operatingSystem: String, val architecture: String, val filePrefix: String, val fileExtension: String, val classifier: String) {
    MACOS_AARCH64("aarch64-apple-darwin", Os.FAMILY_MAC, "aarch64", "lib", "dylib", "darwin"),
    MACOS_X64("x86_64-apple-darwin", Os.FAMILY_MAC, "x86_64", "lib", "dylib", "darwin"),
    WINDOWS_X64("x86_64-pc-windows-gnu", Os.FAMILY_WINDOWS, "x86_64", "", "dll", "windows"),
    WINDOWS_X86("i686-pc-windows-gnu", Os.FAMILY_WINDOWS, "i686", "", "dll", "windows"),
    LINUX_X64("x86_64-unknown-linux-gnu", Os.FAMILY_UNIX, "x86_64", "lib", "so", "linux"),
    LINUX_AMD64("x86_64-unknown-linux-gnu", Os.FAMILY_UNIX, "amd64", "lib", "so", "linux"),
    LINUX_X86("i686-unknown-linux-gnu", Os.FAMILY_UNIX, "i686", "lib", "so", "linux"),
    LINUX_AARCH64("aarch64-unknown-linux-gnu", Os.FAMILY_UNIX, "aarch64", "lib", "so", "linux")
}
