/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import com.swirlds.cli.utility.ParameterizedClass;

/**
 * A state editor command.
 */
public abstract class StateEditorOperation extends ParameterizedClass implements Runnable {

    private StateEditor stateEditor;

    /**
     * Set the state editor instance.
     */
    public void setStateEditor(final StateEditor stateEditor) {
        this.stateEditor = stateEditor;
    }

    /**
     * Get the state editor instance.
     */
    public StateEditor getStateEditor() {
        if (stateEditor == null) {
            throw new IllegalStateException("State editor not set");
        }
        return stateEditor;
    }
}
