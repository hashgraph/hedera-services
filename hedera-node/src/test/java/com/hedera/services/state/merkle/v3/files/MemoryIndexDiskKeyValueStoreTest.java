package com.hedera.services.state.merkle.v3.files;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class MemoryIndexDiskKeyValueStoreTest {
    @Test
    public void createDataAndCheck() throws Exception {
        Path tempDir = Files.createTempDirectory("DataFileTest");

//        DataFile dataFile = new DataFile();

        // clean up and delete files
        deleteDirectoryAndContents(tempDir);
    }


    public static void deleteDirectoryAndContents(Path dir) {
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try {
                //noinspection ResultOfMethodCallIgnored
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                Files.deleteIfExists(dir);
            } catch (Exception e) {
                System.err.println("Failed to delete test directory ["+dir.toFile().getAbsolutePath()+"]");
                e.printStackTrace();
            }
            System.out.println("Deleted data files");
        }
    }
}
