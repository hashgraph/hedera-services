/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.keys;

import static com.hedera.services.bdd.spec.keys.SigControl.Nature.SIG_OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.Nature.SIG_ON;
import static java.util.stream.Collectors.joining;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class KeyLabel {
    public enum Kind {
        SIMPLE,
        COMPLEX
    }

    private String label;
    private KeyLabel[] constituents;

    private final Kind kind;

    private KeyLabel(Kind kind, String label) {
        this.kind = kind;
        this.label = label;
    }

    private KeyLabel(Kind kind, KeyLabel[] constituents) {
        this.kind = kind;
        this.constituents = constituents;
    }

    public KeyLabel[] getConstituents() {
        return constituents;
    }

    public String literally() {
        return label;
    }

    public static KeyLabel simple(String label) {
        return new KeyLabel(Kind.SIMPLE, label);
    }

    public static KeyLabel complex(Object... objs) {
        KeyLabel[] constituents =
                Stream.of(objs)
                        .map(obj -> (obj instanceof KeyLabel) ? obj : simple((String) obj))
                        .toArray(n -> new KeyLabel[n]);
        return new KeyLabel(Kind.COMPLEX, constituents);
    }

    public static KeyLabel uniquelyLabeling(SigControl control) {
        return uniquelyLabeling(control, new AtomicInteger(0));
    }

    private static KeyLabel uniquelyLabeling(SigControl control, AtomicInteger id) {
        if (EnumSet.of(SIG_ON, SIG_OFF).contains(control.getNature())) {
            int idHere = id.incrementAndGet();
            return simple("" + idHere);
        } else {
            SigControl[] children = control.getChildControls();
            return complex(Stream.of(children).map(child -> uniquelyLabeling(child, id)).toArray());
        }
    }

    @Override
    public String toString() {
        switch (kind) {
            default:
                return label;
            case COMPLEX:
                return new StringBuilder()
                        .append("[")
                        .append(
                                Stream.of(constituents)
                                        .map(KeyLabel::toString)
                                        .collect(joining(", ")))
                        .append("]")
                        .toString();
        }
    }
}
