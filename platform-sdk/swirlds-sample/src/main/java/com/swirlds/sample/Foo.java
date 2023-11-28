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

package com.swirlds.sample;

import com.github.javafaker.Faker;
import com.swirlds.base.ArgumentUtils;
import com.swirlds.base.time.Time;
import edu.umd.cs.findbugs.annotations.NonNull;

public class Foo {

    public Foo(@NonNull String name) {
        ArgumentUtils.throwArgBlank(name, "name");
        final String s = new Faker().name().firstName();
        System.out.println("Hello " + s);
    }

    @NonNull
    public static Time getTime() {
        return Time.getCurrent();
    }
}
