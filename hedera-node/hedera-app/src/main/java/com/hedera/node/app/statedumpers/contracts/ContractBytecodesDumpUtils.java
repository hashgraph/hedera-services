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

package com.hedera.node.app.statedumpers.contracts;

import static com.hedera.node.app.service.mono.statedumpers.contracts.ContractBytecodesDumpUtils.generateReport;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.contracts.BBMContract;
import com.hedera.node.app.service.mono.statedumpers.contracts.Contracts;
import com.hedera.node.app.service.mono.statedumpers.contracts.Validity;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ContractBytecodesDumpUtils {

    private ContractBytecodesDumpUtils() {
        // Utility class
    }

    public static void dumpModContractBytecodes(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<ContractID>, OnDiskValue<Bytecode>> contracts,
            @NonNull final DumpCheckpoint checkpoint) {
        final var dumpableAccounts = gatherModContracts(contracts);
        final var sb = generateReport(dumpableAccounts);
        try (@NonNull final var writer = new Writer(path)) {
            writer.writeln(sb.toString());
            System.out.printf(
                    "=== contract bytecodes report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    public static Contracts gatherModContracts(VirtualMap<OnDiskKey<ContractID>, OnDiskValue<Bytecode>> contracts) {
        final var contractsToReturn = new ConcurrentLinkedQueue<BBMContract>();
        final var threadCount = 8;
        final var processed = new AtomicInteger();

        try {
            VirtualMapLike.from(contracts)
                    .extractVirtualMapData(
                            getStaticThreadManager(),
                            p -> {
                                processed.incrementAndGet();
                                contractsToReturn.add(fromMod(p.left(), p.right()));
                            },
                            threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of contracts virtual map interrupted!");
            Thread.currentThread().interrupt();
        }

        final var contractArr = contractsToReturn.toArray(new BBMContract[0]);
        System.out.printf("=== %d contracts iterated over (%d saved)%n", processed.get(), contractArr.length);
        return new Contracts(List.of(contractArr), List.of(), contractArr.length);
    }

    public static BBMContract fromMod(OnDiskKey<ContractID> id, OnDiskValue<Bytecode> bytecode) {
        final var c =
                new BBMContract(new TreeSet<>(), bytecode.getValue().code().toByteArray(), Validity.ACTIVE);
        if (id.getKey().contractNum() != null) {
            c.ids().add(id.getKey().contractNum().intValue());
        }
        return c;
    }
}
