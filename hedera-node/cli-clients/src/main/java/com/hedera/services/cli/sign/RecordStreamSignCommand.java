/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.cli.sign;

import com.google.common.annotations.VisibleForTesting;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.cli.SignCommand;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.security.KeyPair;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * A subcommand of the {@link SignCommand}, for signing record stream files
 */
@CommandLine.Command(name = "sign", mixinStandardHelpOptions = true, description = "Sign record stream files")
@SubcommandOf(RecordStreamCommand.class)
public final class RecordStreamSignCommand extends SignCommand {
    @Option(
            names = {"-hv", "--hapiVersion"},
            arity = "1",
            description =
                    "Hapi protobuf version. This is the hapi-version value in hedera-services/settings.gradle.kts. Mandatory!")
    String hapiVersion;
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean generateSignatureFile(
            @NonNull Path signatureFileDestination, @NonNull Path fileToSign, @NonNull KeyPair keyPair) {

        return RecordStreamSigningUtils.signRecordStreamFile(
                signatureFileDestination, fileToSign, keyPair, hapiVersion);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFileSupported(@NonNull final Path path) {
        return RecordStreamType.getInstance().isStreamFile(path.toFile())
                || RecordStreamType.getInstance().isGzFile(path.toFile().getName());
    }

    @VisibleForTesting
    public void setHapiVersion(String hapiVersion) {
        this.hapiVersion = hapiVersion;
    }
}
