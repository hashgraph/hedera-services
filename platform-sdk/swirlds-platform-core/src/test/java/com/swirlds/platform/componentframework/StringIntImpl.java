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

package com.swirlds.platform.componentframework;

/**
 * A test {@link StringIntProcessor} that converts {@link String}s and {@link Integer}s to {@link Long}s and passes
 * them to a {@link LongProcessor}.
 */
public class StringIntImpl implements StringIntProcessor {
    private final LongProcessor longProcessor;

    public StringIntImpl(final LongProcessor longProcessor) {
        this.longProcessor = longProcessor;
    }

    @Override
    public void string(final String s) throws InterruptedException {
        longProcessor.processLong(Long.parseLong(s));
    }

    @Override
    public void number(final Integer i) throws InterruptedException {
        longProcessor.processLong(i);
    }
}
