/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.statedumpers.contracts;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * A contract - some bytecode associated with its contract id(s)
 *
 * @param ids - direct from the signed state file there's one contract id for each bytecode, but
 *     there are duplicates which can be coalesced and then there's a set of ids for the single
 *     contract; kept in sorted order by the container `TreeSet` so it's easy to get the canonical
 *     id for the contract, and also you can't forget to process them in a deterministic order
 * @param bytecode - bytecode of the contract
 * @param validity - whether the contract is valid or note, aka active or deleted
 */
public record BBMContract(
        @NonNull TreeSet</*@NonNull*/ Integer> ids, @NonNull byte[] bytecode, @NonNull Validity validity) {

    // For any set of contract ids with the same bytecode, the lowest contract id is used as the "canonical"
    // id for that bytecode (useful for ordering contracts deterministically)
    public int canonicalId() {
        return ids.first();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null) {
            return false;
        }
        if (o == this) {
            return true;
        }
        return o instanceof BBMContract other
                && new EqualsBuilder()
                        .append(ids, other.ids)
                        .append(bytecode, other.bytecode)
                        .append(validity, other.validity)
                        .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(ids)
                .append(bytecode)
                .append(validity)
                .toHashCode();
    }

    @Override
    public String toString() {
        var csvIds = ids.stream().map(Object::toString).collect(Collectors.joining(","));
        return "Contract{ids=(%s), %s, bytecode=%s}".formatted(csvIds, validity, Arrays.toString(bytecode));
    }
}
