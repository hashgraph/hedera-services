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
import static com.swirlds.platform.state.editor.StateEditorUtils.formatNodeType;
import static com.swirlds.platform.state.editor.StateEditorUtils.formatParent;
import static com.swirlds.platform.state.editor.StateEditorUtils.formatRoute;

import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.route.MerkleRouteIterator;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import picocli.CommandLine;

@CommandLine.Command(
        name = "load",
        mixinStandardHelpOptions = true,
        description = "load a subtree from file and put it into the state")
@SubcommandOf(StateEditorRoot.class)
public class StateEditorLoad extends StateEditorOperation {

    private String destinationPath = "";
    private Path fileName;

    @CommandLine.Parameters(index = "0", description = "The location on disk where the subtree should be loaded from.")
    private void setFileName(final Path fileName) {
        this.fileName = pathMustExist(getAbsolutePath(fileName));
    }

    @CommandLine.Parameters(index = "1", arity = "0..1", description = "The route where the node should be copied to.")
    private void setDestinationPath(final String destinationPath) {
        this.destinationPath = destinationPath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        final StateEditor.ParentInfo parentInfo = getStateEditor().getParentInfo(destinationPath);
        final MerkleRoute destinationRoute = parentInfo.target();
        final MerkleInternal parent = parentInfo.parent();
        final int indexInParent = parentInfo.indexInParent();

        System.out.println("Loading subtree from " + formatFile(fileName));

        final MerkleNode subtree;
        try (final MerkleDataInputStream in =
                new MerkleDataInputStream(new BufferedInputStream(new FileInputStream(fileName.toFile())))) {

            subtree = in.readMerkleTree(fileName.getParent(), Integer.MAX_VALUE);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        System.out.println("Loaded " + formatNodeType(subtree) + " subtree into " + formatRoute(destinationRoute)
                + ", new parent is " + formatParent(parent, indexInParent));

        parent.asInternal().setChild(indexInParent, subtree);

        // Invalidate hashes in path down from root
        new MerkleRouteIterator(getStateEditor().getState(), parent.getRoute())
                .forEachRemaining(Hashable::invalidateHash);
    }
}
