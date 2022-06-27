import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

public class ResultArchivingExtension implements AfterAllCallback {

    @Override
    public void afterAll(final ExtensionContext ctx) throws Exception {
        final String className;

        if (ctx.getTestClass().isPresent()) {
            className = ctx.getTestClass().get().getSimpleName();
        } else {
            className = ctx.getDisplayName();
        }

        archiveRunData(className);
    }

    private static void archiveRunData(final String className) throws IOException {
        final File workspace = new File(System.getProperty("networkWorkspaceDir"));
        final File workspaceArchive = new File(workspace, "archive");

        if (!workspace.exists()) {
            return;
        }

        final File archiveFolder = new File(workspaceArchive,
                String.format("%s%s%s", className, File.separator, Instant.now().toString()));
        final File[] workspaceFiles = workspace.listFiles(
                (dir, name) -> name != null && !name.trim().equals(workspaceArchive.getName()));

        if (workspaceFiles == null || workspaceFiles.length == 0) {
            return;
        }

        if (!archiveFolder.exists()) {
            if (!archiveFolder.mkdirs()) {
                throw new FileNotFoundException(archiveFolder.getAbsolutePath());
            }
        }

        for (final File f : workspaceFiles) {
            Files.move(f.toPath(), archiveFolder.toPath().resolve(f.getName()), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
