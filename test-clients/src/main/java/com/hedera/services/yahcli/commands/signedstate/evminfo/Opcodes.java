/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.yahcli.commands.signedstate.evminfo;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Contains a type (Descr) describing an EVM opcode, and table lookup
 *
 * <p>Contained record type Descr describes an EVM opcode, and there is table lookup to get from a
 * byte to the opcode (mnemonic) or the other way around.
 */
public class Opcodes {

    // Describes an EVM opcode
    // - extraBytes is the # of bytes in the codestream, following the opcode, which are
    //   part of this instruction (the `PUSHnn` instructions only)
    // - assigned is true for all _assigned_ opcodes (including 0xFE `INVALID`)

    /**
     * Describes an EVM opcode
     *
     * @param opcode - value of the opcode, e.g., 0x62 for a "push 2 bytes" instruction
     * @param extraBytes - how many extra bytes go with this opcode, e.g., 2 for "push 2 bytes"
     * @param mnemonic - mnemonic for this opcode, e.g., "PUSH2" for "push 2 bytes" instruction
     * @param assigned - true if this is an _assigned_ EVM opcode (e.g., a _legal_ opcode)
     */
    public record Descr(int opcode, int extraBytes, String mnemonic, boolean assigned) {

        public Descr(int opcode, final String mnemonic) {
            this(opcode, 0, mnemonic, true);
        }

        public Descr(int opcode, final String mnemonic, boolean assigned) {
            this(opcode, 0, mnemonic, assigned);
        }

        public Descr(int opcode, int extraBytes, final String mnemonic) {
            this(opcode, extraBytes, mnemonic, true);
        }
    }

    /** Table of opcodes, indexed by opcode */
    private static final ImmutableList<Descr> byOpcode;

    /** Get an opcode Descr, given opcode value */
    public static @NonNull Descr getOpcode(int opcode) {
        return byOpcode.get(opcode & 0xFF);
    }

    /** Table of opcodes, indexed by mnemonic */
    private static final ImmutableSortedMap<String, Descr> byMnemonic;

    /** Get an opcode Descr, given mnemonic */
    public static @NonNull Descr getDescrFor(@NonNull final String mnemonic) {
        final var descr = Opcodes.byMnemonic.get(mnemonic.toUpperCase());
        if (null == descr)
            throw new IllegalArgumentException(
                    String.format("opcode '%s' not found in table", mnemonic));
        return descr;
    }

    static {
        // Create the opcode tables ... see https://ethereum.org/en/developers/docs/evm/opcodes/

        var descrs = new ArrayList<Descr>(256);

        // Start with all the unique/ordinary opcodes
        """
          00 STOP
          01 ADD
          02 MUL
          03 SUB
          04 DIV
          05 SDIV
          06 MOD
          07 SMOD
          08 ADDMOD
          09 MULMOD
          0A EXP
          0B SIGNEXTEND
          10 LT
          11 GT
          12 SLT
          13 SGT
          14 EQ
          15 ISZERO
          16 AND
          17 OR
          18 XOR
          19 NOT
          1A BYTE
          1B SHL
          1C SHR
          1D SAR
          20 KECCAK256
          30 ADDRESS
          31 BALANCE
          32 ORIGIN
          33 CALLER
          34 CALLVALUE
          35 CALLDATALOAD
          36 CALLDATASIZE
          37 CALLDATACOPY
          38 CODESIZE
          39 CODECOPY
          3A GASPRICE
          3B EXTCODESIZE
          3C EXTCODECOPY
          3D RETURNDATASIZE
          3E RETURNDATACOPY
          3F EXTCODEHASH
          40 BLOCKHASH
          41 COINBASE
          42 TIMESTAMP
          43 NUMBER
          44 PREVRANDAO
          45 GASLIMIT
          46 CHAIND
          47 SELFBALANCE
          48 BASEFEE
          50 POP
          51 MLOAD
          52 MSTORE
          53 MSTORE8
          54 SLOAD
          55 SSTORE
          56 JUMP
          57 JUMPI
          58 PC
          59 MSIZE
          5A GAS
          5B JUMPDEST
          F0 CREATE
          F1 CALL
          F2 CALLCODE
          F3 RETURN
          F4 DELEGATECALL
          F5 CREATE2
          FA STATICCALL
          FD REVERT
          FE INVALID
          FF SELFDESTRUCT
          """
                .lines()
                .forEach(
                        line -> {
                            final var no = line.split(" ");
                            final var op = Integer.parseUnsignedInt(no[0], 16);
                            descrs.add(new Descr(op, no[1]));
                        });

        // Add all the "multiple" opcodes
        for (int i = 1; i <= 32; i++) {
            descrs.add(new Descr(0x60 + i - 1, i, "PUSH" + Integer.toString(i)));
        }
        for (int i = 1; i <= 16; i++) {
            descrs.add(new Descr(0x80 + i - 1, "DUP" + Integer.toString(i)));
        }
        for (int i = 1; i <= 16; i++) {
            descrs.add(new Descr(0x90 + i - 1, "SWAP" + Integer.toString(i)));
        }
        for (int i = 0; i <= 4; i++) {
            descrs.add(new Descr(0xA0 + i, "LOG" + Integer.toString(i)));
        }
        // Add all the unassigned (thus invalid) opcodes
        concatStreams(
                        intRange(0x0C, 0x0F),
                        intRange(0x1E, 0x1F),
                        intRange(0x21, 0x2F),
                        intRange(0x49, 0x4F),
                        intRange(0x5C, 0x5F),
                        intRange(0xA5, 0xEF),
                        intRange(0xF6, 0xF9),
                        intRange(0xFB, 0xFC))
                // (Say, you'd think Guava's `RangeSet` would be perfect for this ... except you
                // can't iterate it.  A set which you can't iterate over its members ... it's a good
                // idea, because ... I dunno ...)
                .forEach(
                        i -> {
                            final var n = String.format("%02X", i);
                            descrs.add(new Descr(i, "UNASSIGNED-" + n, false));
                        });

        // Validate we've got all 256 opcodes defined
        final var allOpcodes = descrs.stream().map(Descr::opcode).collect(toSet());

        // sanity check this data constructor ...
        if (256 != allOpcodes.size()) {
            throw new IllegalStateException(
                    String.format(
                            "EVM opcode table incomplete, only %s opcodes present",
                            allOpcodes.size()));
        }

        descrs.sort(Comparator.comparingInt(Descr::opcode));
        byOpcode = ImmutableList.copyOf(descrs);
        byMnemonic =
                ImmutableSortedMap.copyOf(descrs.stream().collect(toMap(Descr::mnemonic, d -> d)));
    }

    /** Returns a Stream<Integer> of a range of integers, inclusive of both ends */
    private static Stream<Integer> intRange(int low, int high) {
        return IntStream.rangeClosed(low, high).boxed();
    }

    /** Variadic Stream concatenation (why isn't this part of Java Streams class?) */
    @SafeVarargs
    private static @NonNull Stream<Integer> concatStreams(final Stream<Integer>... sis) {
        Stream<Integer> s = Stream.empty();
        for (var str : sis) s = Stream.concat(s, str);
        return s;
    }

    private Opcodes() {
        throw new UnsupportedOperationException("Utility class");
    }
}
