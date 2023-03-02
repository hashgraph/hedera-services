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
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import java.util.concurrent.ExecutionException;
import picocli.CommandLine;

@CommandLine.Command(name = "hash", mixinStandardHelpOptions = true, description = "Hash unhashed nodes the state.")
@SubcommandOf(StateEditorRoot.class)
public class StateEditorHash extends StateEditorOperation {

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        try {
            MerkleCryptoFactory.getInstance()
                    .digestTreeAsync(getStateEditor().getState())
                    .get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
