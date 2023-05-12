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

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRouteUtils;
import picocli.CommandLine;

@CommandLine.Command(
        name = "resize",
        mixinStandardHelpOptions = true,
        description = "Change the number of children in an internal node.")
// @SubcommandOf(StateEditorRoot.class) // This can be un-commented when resizing is implemented
public class StateEditorResize extends StateEditorOperation {

    private String parentPath = "";
    private int newSize;

    @CommandLine.Parameters(index = "0", description = "The new size of the parent.")
    private void setNewSize(final int newSize) {
        this.newSize = newSize;
    }

    @CommandLine.Parameters(index = "1", arity = "0..1", description = "The route of the parent will be resized.")
    private void setParentPath(final String parentPath) {
        this.parentPath = parentPath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        final MerkleNode parent = getStateEditor().getRelativeNode(parentPath);
        if (parent == null) {
            throw new IllegalArgumentException("The node at " + parentPath + " is null.");
        }
        if (!(parent instanceof MerkleInternal)) {
            throw new IllegalArgumentException("The node at " + parentPath + " is of type "
                    + parent.getClass().getSimpleName() + " and is not an internal node.");
        }

        System.out.println("Resizing [" + MerkleRouteUtils.merkleRouteToPathFormat(parent.getRoute()) + " "
                + parent.getClass().getSimpleName() + "] to child count " + newSize);

        // FUTURE WORK implement merkle resizing... this would be a really handy feature!
    }
}
