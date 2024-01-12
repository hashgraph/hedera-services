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

import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.cli.SignCommand;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.security.KeyPair;
import picocli.CommandLine;

/**
 * A subcommand of the {@link SignCommand}, for signing account balance files
 */
@CommandLine.Command(name = "sign", mixinStandardHelpOptions = true, description = "Sign account balance files")
@SubcommandOf(AccountBalanceCommand.class)
public final class AccountBalanceSignCommand extends SignCommand {
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean generateSignatureFile(
            @NonNull Path signatureFileDestination, @NonNull Path fileToSign, @NonNull KeyPair keyPair) {

        return AccountBalanceSigningUtils.signAccountBalanceFile(signatureFileDestination, fileToSign, keyPair);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFileSupported(@NonNull final Path path) {
        return AccountBalanceType.getInstance().isCorrectFile(path.toFile().getName());
    }
}
