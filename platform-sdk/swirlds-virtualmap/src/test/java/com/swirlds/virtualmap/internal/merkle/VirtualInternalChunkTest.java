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

package com.swirlds.virtualmap.internal.merkle;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VirtualInternalChunkTest {

    @Test
    public void testPow() {
        Assertions.assertEquals(1, VirtualInternalChunk.pow(1, 0));
        Assertions.assertEquals(1, VirtualInternalChunk.pow(2, 0));
        Assertions.assertEquals(1, VirtualInternalChunk.pow(4, 0));
        Assertions.assertEquals(1, VirtualInternalChunk.pow(1, 1));
        Assertions.assertEquals(2, VirtualInternalChunk.pow(2, 1));
        Assertions.assertEquals(4, VirtualInternalChunk.pow(4, 1));
        Assertions.assertEquals(1, VirtualInternalChunk.pow(1, 2));
        Assertions.assertEquals(1, VirtualInternalChunk.pow(1, 4));
        Assertions.assertEquals(16, VirtualInternalChunk.pow(4, 2));
        Assertions.assertEquals(256, VirtualInternalChunk.pow(4, 4));
    }

    @Test
    public void testChunkLevel2() {
        Assertions.assertEquals(1, VirtualInternalChunk.getLevel(1, 2));
        Assertions.assertEquals(2, VirtualInternalChunk.getLevel(2, 2));
        Assertions.assertEquals(2, VirtualInternalChunk.getLevel(3, 2));
        Assertions.assertEquals(2, VirtualInternalChunk.getLevel(5, 2));
        Assertions.assertEquals(3, VirtualInternalChunk.getLevel(6, 2));
        Assertions.assertEquals(3, VirtualInternalChunk.getLevel(21, 2));
        Assertions.assertEquals(4, VirtualInternalChunk.getLevel(22, 2));
    }

    @Test
    public void testChunkLevel3() {
        Assertions.assertEquals(1, VirtualInternalChunk.getLevel(1, 3));
        Assertions.assertEquals(2, VirtualInternalChunk.getLevel(2, 3));
        Assertions.assertEquals(2, VirtualInternalChunk.getLevel(3, 3));
        Assertions.assertEquals(2, VirtualInternalChunk.getLevel(9, 3));
        Assertions.assertEquals(3, VirtualInternalChunk.getLevel(10, 3));
    }

    @Test
    public void testFirstPathInChunk2() {
        Assertions.assertEquals(1, VirtualInternalChunk.firstPathInChunk(1, 2));
        Assertions.assertEquals(7, VirtualInternalChunk.firstPathInChunk(2, 2));
        Assertions.assertEquals(9, VirtualInternalChunk.firstPathInChunk(3, 2));
        Assertions.assertEquals(11, VirtualInternalChunk.firstPathInChunk(4, 2));
        Assertions.assertEquals(13, VirtualInternalChunk.firstPathInChunk(5, 2));
        Assertions.assertEquals(31, VirtualInternalChunk.firstPathInChunk(6, 2));
        Assertions.assertEquals(33, VirtualInternalChunk.firstPathInChunk(7, 2));
        Assertions.assertEquals(35, VirtualInternalChunk.firstPathInChunk(8, 2));
        Assertions.assertEquals(37, VirtualInternalChunk.firstPathInChunk(9, 2));
        Assertions.assertEquals(39, VirtualInternalChunk.firstPathInChunk(10, 2));
        Assertions.assertEquals(41, VirtualInternalChunk.firstPathInChunk(11, 2));
        Assertions.assertEquals(43, VirtualInternalChunk.firstPathInChunk(12, 2));
        Assertions.assertEquals(45, VirtualInternalChunk.firstPathInChunk(13, 2));
        Assertions.assertEquals(47, VirtualInternalChunk.firstPathInChunk(14, 2));
        Assertions.assertEquals(127, VirtualInternalChunk.firstPathInChunk(22, 2));
    }

    @Test
    public void testFirstPathInChunk3() {
        Assertions.assertEquals(1, VirtualInternalChunk.firstPathInChunk(1, 3));
        Assertions.assertEquals(15, VirtualInternalChunk.firstPathInChunk(2, 3));
        Assertions.assertEquals(17, VirtualInternalChunk.firstPathInChunk(3, 3));
        Assertions.assertEquals(19, VirtualInternalChunk.firstPathInChunk(4, 3));
        Assertions.assertEquals(21, VirtualInternalChunk.firstPathInChunk(5, 3));
        Assertions.assertEquals(23, VirtualInternalChunk.firstPathInChunk(6, 3));
        Assertions.assertEquals(25, VirtualInternalChunk.firstPathInChunk(7, 3));
        Assertions.assertEquals(27, VirtualInternalChunk.firstPathInChunk(8, 3));
        Assertions.assertEquals(29, VirtualInternalChunk.firstPathInChunk(9, 3));
        Assertions.assertEquals(127, VirtualInternalChunk.firstPathInChunk(10, 3));
    }

    @Test
    public void testPathToChunk2() {
        Assertions.assertEquals(0, VirtualInternalChunk.pathToChunk(0, 2));
        Assertions.assertEquals(1, VirtualInternalChunk.pathToChunk(1, 2));
        Assertions.assertEquals(1, VirtualInternalChunk.pathToChunk(2, 2));
        Assertions.assertEquals(1, VirtualInternalChunk.pathToChunk(3, 2));
        Assertions.assertEquals(1, VirtualInternalChunk.pathToChunk(6, 2));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(7, 2));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(8, 2));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(15, 2));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(18, 2));
        Assertions.assertEquals(4, VirtualInternalChunk.pathToChunk(11, 2));
        Assertions.assertEquals(4, VirtualInternalChunk.pathToChunk(12, 2));
        Assertions.assertEquals(4, VirtualInternalChunk.pathToChunk(23, 2));
        Assertions.assertEquals(4, VirtualInternalChunk.pathToChunk(26, 2));
        Assertions.assertEquals(5, VirtualInternalChunk.pathToChunk(13, 2));
        Assertions.assertEquals(5, VirtualInternalChunk.pathToChunk(14, 2));
        Assertions.assertEquals(5, VirtualInternalChunk.pathToChunk(27, 2));
        Assertions.assertEquals(5, VirtualInternalChunk.pathToChunk(30, 2));
        Assertions.assertEquals(6, VirtualInternalChunk.pathToChunk(31, 2));
        Assertions.assertEquals(6, VirtualInternalChunk.pathToChunk(32, 2));
        Assertions.assertEquals(8, VirtualInternalChunk.pathToChunk(35, 2));
        Assertions.assertEquals(6, VirtualInternalChunk.pathToChunk(63, 2));
        Assertions.assertEquals(21, VirtualInternalChunk.pathToChunk(62, 2));
        Assertions.assertEquals(21, VirtualInternalChunk.pathToChunk(126, 2));
        Assertions.assertEquals(22, VirtualInternalChunk.pathToChunk(127, 2));
    }

    @Test
    public void testPathToChunk3() {
        Assertions.assertEquals(0, VirtualInternalChunk.pathToChunk(0, 3));
        Assertions.assertEquals(1, VirtualInternalChunk.pathToChunk(1, 3));
        Assertions.assertEquals(1, VirtualInternalChunk.pathToChunk(2, 3));
        Assertions.assertEquals(1, VirtualInternalChunk.pathToChunk(3, 3));
        Assertions.assertEquals(1, VirtualInternalChunk.pathToChunk(6, 3));
        Assertions.assertEquals(1, VirtualInternalChunk.pathToChunk(7, 3));
        Assertions.assertEquals(1, VirtualInternalChunk.pathToChunk(14, 3));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(15, 3));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(16, 3));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(31, 3));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(34, 3));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(63, 3));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(64, 3));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(69, 3));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(70, 3));
        Assertions.assertEquals(3, VirtualInternalChunk.pathToChunk(17, 3));
        Assertions.assertEquals(3, VirtualInternalChunk.pathToChunk(18, 3));
        Assertions.assertEquals(3, VirtualInternalChunk.pathToChunk(35, 3));
        Assertions.assertEquals(3, VirtualInternalChunk.pathToChunk(36, 3));
        Assertions.assertEquals(3, VirtualInternalChunk.pathToChunk(38, 3));
        Assertions.assertEquals(3, VirtualInternalChunk.pathToChunk(71, 3));
        Assertions.assertEquals(3, VirtualInternalChunk.pathToChunk(78, 3));
        Assertions.assertEquals(4, VirtualInternalChunk.pathToChunk(79, 3));
        Assertions.assertEquals(4, VirtualInternalChunk.pathToChunk(86, 3));
        Assertions.assertEquals(8, VirtualInternalChunk.pathToChunk(27, 3));
        Assertions.assertEquals(9, VirtualInternalChunk.pathToChunk(30, 3));
        Assertions.assertEquals(9, VirtualInternalChunk.pathToChunk(62, 3));
        Assertions.assertEquals(9, VirtualInternalChunk.pathToChunk(126, 3));
        Assertions.assertEquals(10, VirtualInternalChunk.pathToChunk(127, 3));
    }

    @Test
    public void testPathToChunk5() {
        Assertions.assertEquals(0, VirtualInternalChunk.pathToChunk(0, 5));
        Assertions.assertEquals(1, VirtualInternalChunk.pathToChunk(1, 5));
        Assertions.assertEquals(1, VirtualInternalChunk.pathToChunk(31, 5));
        Assertions.assertEquals(1, VirtualInternalChunk.pathToChunk(62, 5));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(63, 5));
        Assertions.assertEquals(2, VirtualInternalChunk.pathToChunk(127, 5));
        Assertions.assertEquals(3, VirtualInternalChunk.pathToChunk(65, 5));
        Assertions.assertEquals(33, VirtualInternalChunk.pathToChunk(126, 5));
    }

    @Test
    public void testPathIndexInChunk2() {
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(2, 1).getPathIndexInChunk(0));
        Assertions.assertEquals(0, new VirtualInternalChunk(2, 1).getPathIndexInChunk(1));
        Assertions.assertEquals(1, new VirtualInternalChunk(2, 1).getPathIndexInChunk(2));
        Assertions.assertEquals(2, new VirtualInternalChunk(2, 1).getPathIndexInChunk(3));
        Assertions.assertEquals(3, new VirtualInternalChunk(2, 1).getPathIndexInChunk(4));
        Assertions.assertEquals(4, new VirtualInternalChunk(2, 1).getPathIndexInChunk(5));
        Assertions.assertEquals(5, new VirtualInternalChunk(2, 1).getPathIndexInChunk(6));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(2, 1).getPathIndexInChunk(7));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(2, 2).getPathIndexInChunk(6));
        Assertions.assertEquals(0, new VirtualInternalChunk(2, 2).getPathIndexInChunk(7));
        Assertions.assertEquals(1, new VirtualInternalChunk(2, 2).getPathIndexInChunk(8));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(2, 2).getPathIndexInChunk(9));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(2, 2).getPathIndexInChunk(14));
        Assertions.assertEquals(2, new VirtualInternalChunk(2, 2).getPathIndexInChunk(15));
        Assertions.assertEquals(5, new VirtualInternalChunk(2, 2).getPathIndexInChunk(18));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(2, 2).getPathIndexInChunk(19));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(2, 5).getPathIndexInChunk(12));
        Assertions.assertEquals(0, new VirtualInternalChunk(2, 5).getPathIndexInChunk(13));
        Assertions.assertEquals(1, new VirtualInternalChunk(2, 5).getPathIndexInChunk(14));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(2, 5).getPathIndexInChunk(15));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(2, 5).getPathIndexInChunk(26));
        Assertions.assertEquals(2, new VirtualInternalChunk(2, 5).getPathIndexInChunk(27));
        Assertions.assertEquals(5, new VirtualInternalChunk(2, 5).getPathIndexInChunk(30));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(2, 5).getPathIndexInChunk(31));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(2, 5).getPathIndexInChunk(62));
    }

    @Test
    public void testPathIndexInChunk3() {
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(3, 1).getPathIndexInChunk(0));
        Assertions.assertEquals(0, new VirtualInternalChunk(3, 1).getPathIndexInChunk(1));
        Assertions.assertEquals(1, new VirtualInternalChunk(3, 1).getPathIndexInChunk(2));
        Assertions.assertEquals(6, new VirtualInternalChunk(3, 1).getPathIndexInChunk(7));
        Assertions.assertEquals(13, new VirtualInternalChunk(3, 1).getPathIndexInChunk(14));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(3, 1).getPathIndexInChunk(15));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(3, 2).getPathIndexInChunk(7));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(3, 2).getPathIndexInChunk(14));
        Assertions.assertEquals(0, new VirtualInternalChunk(3, 2).getPathIndexInChunk(15));
        Assertions.assertEquals(1, new VirtualInternalChunk(3, 2).getPathIndexInChunk(16));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(3, 2).getPathIndexInChunk(17));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(3, 2).getPathIndexInChunk(30));
        Assertions.assertEquals(2, new VirtualInternalChunk(3, 2).getPathIndexInChunk(31));
        Assertions.assertEquals(5, new VirtualInternalChunk(3, 2).getPathIndexInChunk(34));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(3, 2).getPathIndexInChunk(35));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(3, 2).getPathIndexInChunk(62));
        Assertions.assertEquals(6, new VirtualInternalChunk(3, 2).getPathIndexInChunk(63));
        Assertions.assertEquals(13, new VirtualInternalChunk(3, 2).getPathIndexInChunk(70));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(3, 2).getPathIndexInChunk(71));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> new VirtualInternalChunk(3, 3).getPathIndexInChunk(70));
        Assertions.assertEquals(0, new VirtualInternalChunk(3, 3).getPathIndexInChunk(17));
        Assertions.assertEquals(2, new VirtualInternalChunk(3, 3).getPathIndexInChunk(35));
        Assertions.assertEquals(6, new VirtualInternalChunk(3, 3).getPathIndexInChunk(71));
    }

    @Test
    public void testMaxChunkBeforePath2() {
        Assertions.assertEquals(1, VirtualInternalChunk.maxChunkBeforePath(1, 2));
        Assertions.assertEquals(1, VirtualInternalChunk.maxChunkBeforePath(2, 2));
        Assertions.assertEquals(1, VirtualInternalChunk.maxChunkBeforePath(6, 2));
        Assertions.assertEquals(2, VirtualInternalChunk.maxChunkBeforePath(7, 2));
        Assertions.assertEquals(3, VirtualInternalChunk.maxChunkBeforePath(9, 2));
        Assertions.assertEquals(5, VirtualInternalChunk.maxChunkBeforePath(13, 2));
        Assertions.assertEquals(5, VirtualInternalChunk.maxChunkBeforePath(14, 2));
        Assertions.assertEquals(5, VirtualInternalChunk.maxChunkBeforePath(15, 2));
        Assertions.assertEquals(5, VirtualInternalChunk.maxChunkBeforePath(19, 2));
        Assertions.assertEquals(5, VirtualInternalChunk.maxChunkBeforePath(30, 2));
        Assertions.assertEquals(6, VirtualInternalChunk.maxChunkBeforePath(31, 2));
        Assertions.assertEquals(8, VirtualInternalChunk.maxChunkBeforePath(35, 2));
        Assertions.assertEquals(21, VirtualInternalChunk.maxChunkBeforePath(62, 2));
        Assertions.assertEquals(21, VirtualInternalChunk.maxChunkBeforePath(63, 2));
        Assertions.assertEquals(21, VirtualInternalChunk.maxChunkBeforePath(99, 2));
        Assertions.assertEquals(21, VirtualInternalChunk.maxChunkBeforePath(126, 2));
        Assertions.assertEquals(22, VirtualInternalChunk.maxChunkBeforePath(127, 2));
    }
}
