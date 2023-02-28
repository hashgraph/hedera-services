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

package com.hedera.services.bdd.spec.fees;

import com.google.common.base.MoreObjects;
import java.util.Objects;

public class Payment {
    public enum Reason {
        TXN_FEE,
        COST_ANSWER_QUERY_COST,
        ANSWER_ONLY_QUERY_COST
    }

    public final long tinyBars;
    public final String opName;
    public final Reason reason;

    public Payment(long tinyBars, String opName, Reason reason) {
        this.tinyBars = tinyBars;
        this.opName = opName;
        this.reason = reason;
    }

    public static Payment fromEntry(String name, long value) {
        String[] parts = name.split("[.]");
        return new Payment(value, parts[0], Reason.valueOf(parts[1]));
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!o.getClass().equals(Payment.class)) {
            return false;
        }

        Payment that = (Payment) o;
        return this.tinyBars == that.tinyBars
                && Objects.equals(this.opName, that.opName)
                && Objects.equals(this.reason, that.reason);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(tinyBars);
        result = result * 31 + opName.hashCode();
        result = result * 31 + reason.hashCode();
        return result;
    }

    public String entryName() {
        return opName + "." + reason;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("op", opName)
                .add("reason", reason)
                .add("tinyBars", tinyBars)
                .toString();
    }
}
