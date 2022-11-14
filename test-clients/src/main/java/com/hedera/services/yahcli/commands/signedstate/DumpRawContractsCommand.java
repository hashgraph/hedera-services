/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.yahcli.commands.signedstate;

import static org.apache.commons.codec.binary.Hex.encodeHexString;

import com.hedera.services.utils.EntityNum;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "dumprawcontracts",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Dumps contract bytecodes in hex")
public class DumpRawContractsCommand implements Callable<Integer> {
    @ParentCommand private SignedStateCommand signedStateCommand;

    @Option(
            names = {"-f", "--file"},
            arity = "1",
            paramLabel = "INPUT-SIGNED-STATE-FILE",
            description = "Input signed state file")
    Path inputFile;

    @Option(
            names = {"-p", "--prefix"},
            description =
                    "Prefix for each contract bytecode line (suitable for finding lines via grep")
    String prefix = "";

    @Option(
            names = {"-#", "--with-ids"},
            description = "Output contract ids too")
    boolean withIds;

    @Override
    public Integer call() throws Exception {
        final var contractContents = getContracts(inputFile).left;
        final var formattedContracts = formatContractLines(contractContents);
        for (var s : formattedContracts) System.out.println(s);

        return 0;
    }

    @Contract(pure = true)
    private @NotNull Collection<String> formatContractLines(
            @NotNull Map<EntityNum, byte[]> contractContents) {
        Collection<String> formattedContracts = new ArrayList<>();
        for (var kv : contractContents.entrySet()) {
            StringBuilder sb =
                    new StringBuilder(
                            kv.getValue().length * 2
                                    + prefix.length()
                                    + 50 /*more than enuf for the rest*/);
            if (!prefix.isEmpty()) {
                sb.append(prefix);
                sb.append('\t');
            }
            sb.append(encodeHexString(kv.getValue()));
            if (withIds) {
                sb.append('\t');
                sb.append(kv.getKey().intValue());
            }
            formattedContracts.add(sb.toString());
        }
        return formattedContracts;
    }

    @Contract(pure = true)
    private @NotNull ImmutablePair<Map<EntityNum, byte[]>, Integer> getContracts(
            @NotNull Path inputFile) throws Exception {
        int contractsFound;
        try (final var signedState = new SignedStateHolder(inputFile)) {
            final var contractIds = signedState.getAllKnownContracts();
            contractsFound = contractIds.size();
            final var contractContents = signedState.getAllContractContents(contractIds);
            return ImmutablePair.of(contractContents, contractsFound);
        }
    }
}
