/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.grid;

import java.io.PrintWriter;

/**
 * An interface for writing a grid as a text string to a PrintWriter
 */
public interface GridRenderer {

    /**
     * Write a text string to a PrintWriter
     */
    default void render() {
        final PrintWriter writer = new PrintWriter(System.out);
        render(writer);
        writer.flush();
        System.out.flush();
    }

    /**
     * An implementor of this function writes a text string to the given writer
     *
     * @param writer
     * 		the PrintWriter
     */
    void render(final PrintWriter writer);
}
