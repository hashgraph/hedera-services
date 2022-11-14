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
package com.hedera.services.yahcli.commands.signedstate.evminfo;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Opcodes {

    public record Descr(int opcode, int extraBytes, String name, boolean valid) {

        public Descr(int opcode, String name) {
            this(opcode, 0, name, true);
        }

        public Descr(int opcode, String name, boolean valid) {
            this(opcode, 0, name, valid);
        }

        public Descr(int opcode, int extraBytes, String name) {
            this(opcode, extraBytes, name, true);
        }
    }

    public static final List<Descr> byOpcode;
    public static final SortedMap<String, Descr> byMnemonic;

    static {
        List<Descr> descrs = new ArrayList<>(256);

        // Start with all of the unique/ordinary opcodes
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
                        (line) -> {
                            var no = line.split(" ");
                            var op = Integer.parseUnsignedInt(no[0], 16);
                            descrs.add(new Descr(op, no[1]));
                        });

        // Add all of the "multiple" opcodes
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
        // Add all of the unassigned (thus invalid) opcodes
        concatStreams(
                        intRange(0x0C, 0x0F),
                        intRange(0x1E, 0x1F),
                        intRange(0x21, 0x2F),
                        intRange(0x49, 0x4F),
                        intRange(0x5C, 0x5F),
                        intRange(0xA5, 0xEF),
                        intRange(0xF6, 0xF9),
                        intRange(0xFB, 0xFC))
                .forEach(
                        (i) -> {
                            var n = String.format("%02X", i);
                            descrs.add(new Descr(i, "UNASSIGNED-" + n, false));
                        });

        // Validate we've got all 256 opcodes defined
        var allOpcodes = descrs.stream().map(Descr::opcode).collect(toSet());
        if (256 != allOpcodes.size()) {
            throw new IllegalStateException(
                    String.format(
                            "EVM opcode table incomplete, only %s elements", allOpcodes.size()));
        }

        descrs.sort(Comparator.comparingInt(Descr::opcode));
        byOpcode = descrs;
        byMnemonic =
                new TreeMap<>(descrs.stream().collect(toMap(Descr::name, Function.identity())));
    }

    // Returns a Stream<Integer> of a range of integers, inclusive
    private static Stream<Integer> intRange(int low, int high) {
        return IntStream.rangeClosed(low, high).boxed();
    }

    // Varadic Stream concatenation (why isn't this part of Java Streams class?)
    private static Stream<Integer> concatStreams(Stream<Integer>... sis) {
        Stream<Integer> s = Stream.empty();
        for (var str : sis) s = Stream.concat(s, str);
        return s;
    }
}
