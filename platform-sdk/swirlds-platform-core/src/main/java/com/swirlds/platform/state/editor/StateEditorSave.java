/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.editor;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.platform.state.editor.StateEditorUtils.formatFile;
import static com.swirlds.platform.state.signed.SignedStateFileWriter.writeSignedStateFilesToDirectory;

import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import picocli.CommandLine;

@CommandLine.Command(name = "save", mixinStandardHelpOptions = true, description = "Write the entire state to disk.")
@SubcommandOf(StateEditorRoot.class)
public class StateEditorSave extends StateEditorOperation {

    private Path directory;

    @CommandLine.Parameters(description = "The directory where the saved state should be written.")
    private void setFileName(final Path directory) {
        this.directory = pathMustNotExist(getAbsolutePath(directory));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        try {
            System.out.println("Hashing state");
            MerkleCryptoFactory.getInstance()
                    .digestTreeAsync(getStateEditor().getState())
                    .get();

            System.out.println("Writing signed state file to " + formatFile(directory));

            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }

            writeSignedStateFilesToDirectory(directory, getStateEditor().getSignedStateCopy());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
