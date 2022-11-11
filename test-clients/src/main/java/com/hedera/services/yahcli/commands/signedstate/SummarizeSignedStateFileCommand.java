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

import com.hedera.services.ServicesState;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobKey.Type;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "summarize",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Summarizes contents of signed state file")
public class SummarizeSignedStateFileCommand implements Callable<Integer> {
    @ParentCommand private SignedStateCommand signedStateCommand;

    @Option(
            names = {"-f", "--file"},
            arity = "1",
            paramLabel = "INPUT-SIGNED-STATE-FILE",
            description = "Input signed state file")
    Optional<Path> inputFile;

    @Option(
            names = {"-s", "--summarize"},
            description = "Provide summary of what was in the input file")
    Optional<Boolean> doSummary;

    @Override
    public Integer call() throws Exception {
        System.out.printf(
                "SummarizeSignedStateFile: input file %s summarize %s%n",
                inputFile.map(Path::toString).orElse("<NONE>"),
                doSummary.isPresent() ? "YES" : "NO");

        ConstructableRegistry.getInstance().registerConstructables("*");
        var signedPair = SignedStateFileReader.readStateFile(inputFile.get());
        var servicesState = (ServicesState) (signedPair.signedState().getState().getSwirldState());
        var accounts = servicesState.accounts();
        List<EntityNum> contractIds = new ArrayList<>();
        accounts.forEach(
                (k, v) -> {
                    if (v.isSmartContract()) contractIds.add(k);
                });

        var fileStore = servicesState.storage();
        int contractsFound = 0;
        int bytesFound = 0;
        for (var cid : contractIds) {
            VirtualBlobKey vbk = new VirtualBlobKey(Type.CONTRACT_BYTECODE, cid.intValue());
            if (fileStore.containsKey(vbk)) {
                var blob = fileStore.get(vbk); // check exists?
                var maybeByteCodeHere = blob.getData();
                contractsFound++;
                bytesFound += maybeByteCodeHere.length;
            }
        }
        System.out.printf(
                "SummarizeSignedStateFile: %d contractIDs found %d contracts found in file store"
                        + " (%d bytes total)",
                contractIds.size(), contractsFound, bytesFound);

        return 0;
    }
}
