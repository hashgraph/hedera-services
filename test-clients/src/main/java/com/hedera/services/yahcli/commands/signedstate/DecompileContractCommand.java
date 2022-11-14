/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static java.lang.Integer.min;
import static java.util.Arrays.copyOf;

import com.hedera.services.yahcli.commands.signedstate.evminfo.Opcodes;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "decompilecontract",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Decompiles contract bytecodes")
public class DecompileContractCommand implements Callable<Integer> {
    @ParentCommand private SignedStateCommand signedStateCommand;

    @Option(
            names = {"-b", "--bytecode"},
            required = true,
            arity = "1",
            converter = HexStringConverter.class,
            paramLabel = "BYTECODE_HEX",
            description = "Contract bytecode as a hex string")
    HexStringConverter.Bytes theContract;

    @Option(
            names = {"-i", "--id"},
            paramLabel = "CONTRACT_ID",
            description = "Contract Id (decimal, optional)")
    Optional<Long> theContractId;

    @Override
    public Integer call() throws Exception {

        final int DISPLAY_LENGTH = 100;
        int contractLength = theContract.bytes.length;
        int displayLength = min(DISPLAY_LENGTH, contractLength);
        short[] displayContract = copyOf(theContract.bytes, displayLength);

        System.out.printf("DecompileContract: contract id %d%n", theContractId.orElse(-1L));
        System.out.printf("   first bytes of contract: %s%n", toHex(displayContract));
        System.out.printf("   BTW, have %d opcodes defined%n", Opcodes.byOpcode.size());
        for (var ds : Opcodes.byOpcode) {
            System.out.printf(
                    "      %02X: %s%s%s%n",
                    ds.opcode(),
                    ds.name(),
                    ds.extraBytes() > 0
                            ? (" (" + Integer.toString(ds.extraBytes()) + " extra)")
                            : "",
                    ds.valid() ? "" : " (INVALID)");
        }
        return 0;
    }

    @Contract(pure = true)
    private @NotNull String toHex(short @NotNull [] byteBuffer) {
        final String hexits = "0123456789abcdef";
        StringBuffer sb = new StringBuffer(2 * byteBuffer.length);
        for (short byt : byteBuffer) {
            sb.append(hexits.charAt(byt >> 4));
            sb.append(hexits.charAt(byt & 0xf));
        }
        return sb.toString();
    }
}
