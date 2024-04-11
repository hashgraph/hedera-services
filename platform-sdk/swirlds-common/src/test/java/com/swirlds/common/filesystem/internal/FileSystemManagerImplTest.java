/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.filesystem.internal;

/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import com.swirlds.base.test.fixtures.concurrent.TestExecutor;
import com.swirlds.base.test.fixtures.concurrent.WithTestExecutor;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.io.utility.RecycleBin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@WithTestExecutor
public class FileSystemManagerImplTest {

    @Mock
    private RecycleBin mockRecycleBin;

    @TempDir
    private Path rootPathParent;

    public FileSystemManagerImpl getFileSystemManager() {
        return new FileSystemManagerImpl(getTestRootPath(), p -> mockRecycleBin);
    }

    private String getTestRootPath() {
        return rootPathParent.resolve("FileSystemManagerImplTest").toString();
    }

    @Test
    public void testNew_rootPathIsCreated() {
        // given
        getFileSystemManager();
        // then
        assertThat(Path.of(getTestRootPath()))
                .hasParent(rootPathParent)
                .isDirectory()
                .isNotEmptyDirectory();
    }

    @Test
    public void testNew_aLargeRootPathIsCreated() {
        // given
        final String largeRootLocation =
                new StringBuffer(getTestRootPath()).repeat("/child", 100).toString();
        new FileSystemManagerImpl(largeRootLocation, p -> mockRecycleBin);
        // then
        assertThat(Path.of(largeRootLocation)).isDirectory().isNotEmptyDirectory();
    }

    @Test
    public void testNew_deletesAllIfPathExist() throws IOException {
        // given
        final Path dir = Path.of(getTestRootPath());
        Files.createDirectories(dir);
        final List<String> tmpFileNames = IntStream.range(0, 10)
                .boxed()
                .map(x -> FileUtils.rethrowIO(() -> Files.createTempFile(dir, x + "", null)))
                .map(p -> p.toFile().getName())
                .toList();
        // when
        getFileSystemManager();

        // then
        assertThat(dir)
                .isNotEmptyDirectory()
                .isDirectoryContaining("glob:**tmp")
                .isDirectoryContaining("glob:**usr")
                .isDirectoryContaining("glob:**bin")
                .isDirectoryNotContaining("glob:{" + String.join(",", tmpFileNames) + "}");
    }

    @Test
    public void testResolve_validRelativePath() {
        // given
        final FileSystemManagerImpl fileSystemManager = getFileSystemManager();
        final Path relativePath = Paths.get("data/file.txt");
        final Path resolvedPath = fileSystemManager.resolve(relativePath);

        // then
        // Assert that the resolved path is correctly formed from root and relative path
        assertThat(resolvedPath).isEqualTo(Paths.get(getTestRootPath(), "usr/data/file.txt"));
    }

    @Test
    public void testResolve_emptyPath() {
        // given
        final FileSystemManagerImpl fileSystemManager = getFileSystemManager();
        final Path relativeEmptyPath = Paths.get("");

        // then
        // Assert that empty path should not resolve to the root path
        assertThrows(
                IllegalArgumentException.class,
                () -> fileSystemManager.resolve(relativeEmptyPath),
                () -> "Requested path is cannot be converted to valid relative path inside of:" + getTestRootPath());
    }

    @Test
    public void testResolve_absolutePath() {
        // given
        final FileSystemManagerImpl fileSystemManager = getFileSystemManager();
        final Path absolutePath = Paths.get("/home/user/file.txt");

        // then
        // Assert that absolute paths are not allowed
        assertThrows(
                IllegalArgumentException.class,
                () -> fileSystemManager.resolve(absolutePath),
                () -> "Requested path is cannot be converted to valid relative path inside of:" + getTestRootPath());
    }

    @Test
    public void testResolve_pathEscapingRoot() {
        // given
        final FileSystemManagerImpl fileSystemManager = getFileSystemManager();
        final Path escapingPath = Paths.get("../etc/passwd");

        // then
        // Assert that paths trying to escape the root are not allowed
        assertThrows(
                IllegalArgumentException.class,
                () -> fileSystemManager.resolve(escapingPath),
                () -> "Requested path is cannot be converted to valid relative path inside of:" + getTestRootPath());
    }

    @Test
    public void testResolveNewTemp_validName() {
        // given
        final FileSystemManagerImpl fileSystemManager = getFileSystemManager();
        final String name = "myTempFile";
        final Path tempPath = fileSystemManager.resolveNewTemp(name);

        // then
        // Assert that the temporary path has the expected format
        assertThat(tempPath)
                .doesNotExist()
                .satisfies(p -> assertThat(p.toAbsolutePath().toString()).contains(getTestRootPath() + "/tmp"))
                .satisfies(p -> assertThat(p.getFileName().toString()).endsWith("myTempFile"));
    }

    @Test
    public void testRecycle_validRelativePath() throws IOException {
        // given
        final FileSystemManagerImpl fileSystemManager = getFileSystemManager();
        final Path relativePath = Paths.get("data/file.txt");
        fileSystemManager.resolve(relativePath); // Simulate file creation

        doNothing().when(mockRecycleBin).recycle(any(Path.class));

        // when
        fileSystemManager.recycle(relativePath);

        // then
        // Assert that the recycle bin method was called with the resolved path
        verify(mockRecycleBin).recycle(Paths.get(getTestRootPath(), "/data/file.txt"));
    }

    @Test
    public void testRecycle_validAbsolutePath() throws IOException {
        // given
        final FileSystemManagerImpl fileSystemManager = getFileSystemManager();
        final Path absolutePath = Paths.get(getTestRootPath(), "data/file.txt");

        doNothing().when(mockRecycleBin).recycle(any(Path.class));

        // when
        fileSystemManager.recycle(absolutePath);

        // then
        // Assert that the recycle bin method was called with the resolved path
        verify(mockRecycleBin).recycle(absolutePath);
    }

    @Test
    public void testRecycle_emptyPath() {
        // given
        final FileSystemManagerImpl fileSystemManager = getFileSystemManager();
        // then
        assertThrows(IllegalArgumentException.class, () -> fileSystemManager.recycle(Paths.get("")));
    }

    @Test
    public void testRecycle_absolutePath() {
        // given
        final FileSystemManagerImpl fileSystemManager = getFileSystemManager();
        // then
        assertThrows(IllegalArgumentException.class, () -> fileSystemManager.recycle(Paths.get("/home/user/file.txt")));
    }

    @Test
    public void testRecycle_pathEscapingRoot() {
        // given
        final FileSystemManagerImpl fileSystemManager = getFileSystemManager();
        // then
        assertThrows(IllegalArgumentException.class, () -> fileSystemManager.recycle(Paths.get("../etc/passwd")));
    }

    @Test
    public void testResolveNewTemp_concurrentCallDoesNotThrow(TestExecutor testExecutor) {
        // given
        final FileSystemManagerImpl fileSystemManager = getFileSystemManager();
        Runnable r = () -> fileSystemManager.resolveNewTemp("aTag");
        Runnable[] params = Stream.generate(() -> r).limit(100).toArray(Runnable[]::new);
        // when
        final Executable executable = () -> testExecutor.executeAndWait(params);
        // then
        assertDoesNotThrow(executable);
    }
}
