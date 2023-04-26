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

package com.swirlds.platform.cli;

import com.swirlds.cli.commands.EventStreamCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.common.stream.EventStreamType;
import com.swirlds.platform.util.EventStreamSigningUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.security.KeyPair;
import picocli.CommandLine;

/**
 * A subcommand of the {@link SignCommand}, for signing event stream files
 */
@CommandLine.Command(name = "sign", mixinStandardHelpOptions = true, description = "Sign event stream files")
@SubcommandOf(EventStreamCommand.class)
public final class EventStreamSignCommand extends SignCommand {
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean generateSignatureFile(
            @NonNull Path signatureFileDestination, @NonNull Path fileToSign, @NonNull KeyPair keyPair) {

        return EventStreamSigningUtils.signEventStreamFile(signatureFileDestination, fileToSign, keyPair);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFileSupported(@NonNull final Path path) {
        return EventStreamType.getInstance().isStreamFile(path.toFile());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer call() {
        EventStreamSigningUtils.initializeSystem();

        return super.call();
    }
}
