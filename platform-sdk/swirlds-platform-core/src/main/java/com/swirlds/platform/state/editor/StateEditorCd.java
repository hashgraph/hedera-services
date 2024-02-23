/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.cli.utility.SubcommandOf;
import picocli.CommandLine;

@CommandLine.Command(
        name = "cd",
        mixinStandardHelpOptions = true,
        description = "Change the current working route. Analogous to the cd in bash.")
@SubcommandOf(StateEditorRoot.class)
public class StateEditorCd extends StateEditorOperation {

    private String path = "/";

    @CommandLine.Parameters(arity = "0..1", description = "The route to change to.")
    private void setPath(final String path) {
        this.path = path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        getStateEditor().setCurrentWorkingRoute(getStateEditor().getRelativeRoute(path));
    }
}
