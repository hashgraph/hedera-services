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

package com.hedera.node.app.service.mono.exceptions;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Objects;

/** Utility class holding certain logic-error assertions/validations. */
public class Requires {

    /** Takes pairs of (some object instance, name of that object) and throws if _any_ of the
     * given object instances are null.  Exception thrown includes the name of _all_ such
     * null object instances.  Intended to be used to check _multiple_ arguments of a method
     * that cannot any of them be null, and report _all_ null arguments at the same time.
     *
     * @param args List of object-1, name-1, object-2, name-2, ...
     */
    public static void nonNull(Object... args) {
        if (args.length % 2 != 0) throw new IllegalArgumentException("Must have even number of arguments");

        final var nullNames = new ArrayList<String>();

        for (int i = 0; i < args.length; i += 2) {
            if (Objects.isNull(args[i])) {
                final var name = args[i + 1];
                if (Objects.isNull(name))
                    throw new IllegalArgumentException("argument %d has null name".formatted(i / 2));
                nullNames.add(name.toString());
            }
        }

        if (!nullNames.isEmpty()) {
            final var names = String.join(", ", nullNames);
            final var msg = "null argument%s found: %s".formatted(nullNames.size() == 1 ? "" : "s", names);
            throw new NullArgumentsException(msg);
        }
    }

    public static class NullArgumentsException extends NullPointerException {
        public NullArgumentsException(@NonNull String msg) {
            super(msg);
            Objects.requireNonNull(msg, "msg:String");
        }
    }

    /** This is a utility class */
    private Requires() {}
}
