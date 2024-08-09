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

package com.hedera.services.yahcli.commands.nodes;

import com.hedera.node.app.service.addressbook.AddressBookHelper;
import com.hedera.services.bdd.spec.utilops.inventory.AccessoryUtils;
import com.hedera.services.yahcli.Yahcli;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.ParentCommand;

@CommandLine.Command(
        name = "nodes",
        subcommands = {UpdateCommand.class, CreateCommand.class, DeleteCommand.class},
        description = "Performs DAB nodes operations")
public class NodesCommand implements Callable<Integer> {
    @ParentCommand
    Yahcli yahcli;

    @Override
    public Integer call() throws Exception {
        throw new CommandLine.ParameterException(yahcli.getSpec().commandLine(), "Please specify a nodes subcommand");
    }

    public Yahcli getYahcli() {
        return yahcli;
    }

    /**
     * Given a location and a {@link Yahcli}, validates that a key file exists at the location or throws
     * a {@link picocli.CommandLine.ParameterException} with context on the command line that failed.
     * @param loc the location to check for a key file
     * @param yahcli the {@link Yahcli} to use for context
     */
    static void validateKeyAt(@NonNull final String loc, @NonNull final Yahcli yahcli) {
        final Optional<File> keyFile;
        try {
            keyFile = AccessoryUtils.keyFileAt(loc.substring(0, loc.lastIndexOf('.')));
        } catch (Exception e) {
            throw new CommandLine.ParameterException(
                    yahcli.getSpec().commandLine(), "Could not load a key from '" + loc + "' (" + e.getMessage() + ")");
        }
        if (keyFile.isEmpty()) {
            throw new CommandLine.ParameterException(
                    yahcli.getSpec().commandLine(), "Could not load a key from '" + loc + "'");
        }
    }

    /**
     * Given a location and a {@link Yahcli}, validates that a X.509 certificate exists at the location and
     * returns its encoded bytes, or throws a {@link picocli.CommandLine.ParameterException} with context on the
     * command line that failed.
     * @param loc the location to check for a X.509 certificate
     * @param yahcli the {@link Yahcli} to use for context
     * @return the encoded bytes of the X.509 certificate
     */
    static byte[] validatedX509Cert(@NonNull final String loc, @NonNull final Yahcli yahcli) {
        try {
            return AddressBookHelper.readCertificatePemFile(Paths.get(loc)).getEncoded();
        } catch (IOException | CertificateException e) {
            throw new CommandLine.ParameterException(
                    yahcli.getSpec().commandLine(), "Could not load a certificate from '" + loc + "'");
        }
    }
}
