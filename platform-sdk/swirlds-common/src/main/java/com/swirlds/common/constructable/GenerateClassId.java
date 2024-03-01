/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.constructable;

import java.util.Random;

/**
 * Generates random long to use as a class ID for RuntimeConstructable.
 */
public final class GenerateClassId {

    private GenerateClassId() {}

    public static void main(final String[] args) {
        generateAndPrintClassId();
    }

    /**
     * Generate a class ID and print it to standard out.
     */
    public static void generateAndPrintClassId() {
        System.out.printf(
                """

						\tprivate static final long CLASS_ID = 0x%sL;

						\tprivate static final class ClassVersion {
						\t\tpublic static final int ORIGINAL = 1;
						\t}

						\t/**
						\t * {@inheritDoc}
						\t */
						\t@Override
						\tpublic long getClassId() {
						\t\treturn CLASS_ID;
						\t}

						\t/**
						\t * {@inheritDoc}
						\t */
						\t@Override
						\tpublic int getVersion() {
						\t\treturn ClassVersion.ORIGINAL;
						\t}
						""",
                Long.toHexString(new Random().nextLong()));
    }
}
