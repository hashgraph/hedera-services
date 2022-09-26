/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.accounts;

import com.google.common.base.MoreObjects;

public class TestAccount {
    public enum Allowance {
        MISSING,
        INSUFFICIENT,
        OK
    }

    public static final long DEFAULT_TOKEN_THING = 123L;

    public long value;
    public long tokenThing;
    public Object thing;
    public boolean flag;
    public Allowance validHbarAllowances = Allowance.MISSING;
    public Allowance validFungibleAllowances = Allowance.MISSING;
    public Allowance validNftAllowances = Allowance.MISSING;

    public TestAccount() {
        tokenThing = DEFAULT_TOKEN_THING;
    }

    public TestAccount(long value, Object thing, boolean flag, long tokenThing) {
        this(
                value,
                thing,
                flag,
                tokenThing,
                Allowance.MISSING,
                Allowance.MISSING,
                Allowance.MISSING);
    }

    public TestAccount(
            long value,
            Object thing,
            boolean flag,
            long tokenThing,
            Allowance validHbarAllowances,
            Allowance validFungibleAllowances,
            Allowance validNftAllowances) {
        this.value = value;
        this.thing = thing;
        this.flag = flag;
        this.tokenThing = tokenThing;
        this.validHbarAllowances = validHbarAllowances;
        this.validFungibleAllowances = validFungibleAllowances;
        this.validNftAllowances = validNftAllowances;
    }

    public TestAccount(long value, Object thing, boolean flag) {
        this(
                value,
                thing,
                flag,
                DEFAULT_TOKEN_THING,
                Allowance.MISSING,
                Allowance.MISSING,
                Allowance.MISSING);
    }

    public Object getThing() {
        return thing;
    }

    public void setThing(Object thing) {
        this.thing = thing;
    }

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public long getTokenThing() {
        return tokenThing;
    }

    public void setTokenThing(long tokenThing) {
        this.tokenThing = tokenThing;
    }

    public Allowance getValidHbarAllowances() {
        return validHbarAllowances;
    }

    public void setValidHbarAllowances(final Allowance validHbarAllowances) {
        this.validHbarAllowances = validHbarAllowances;
    }

    public Allowance getValidFungibleAllowances() {
        return validFungibleAllowances;
    }

    public void setValidFungibleAllowances(final Allowance validFungibleAllowances) {
        this.validFungibleAllowances = validFungibleAllowances;
    }

    public Allowance getValidNftAllowances() {
        return validNftAllowances;
    }

    public void setValidNftAllowances(final Allowance validNftAllowances) {
        this.validNftAllowances = validNftAllowances;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof TestAccount)) {
            return false;
        }
        TestAccount that = (TestAccount) o;
        return thing.equals(that.thing)
                && (flag == that.flag)
                && (value == that.value)
                && this.tokenThing == that.tokenThing
                && this.validHbarAllowances == that.validHbarAllowances
                && this.validFungibleAllowances == that.validFungibleAllowances
                && this.validNftAllowances == that.validNftAllowances;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("flag", flag)
                .add("thing", thing)
                .add("value", value)
                .add("tokenThing", tokenThing)
                .add("hbarAllowances", validHbarAllowances)
                .add("fungibleAllowances", validFungibleAllowances)
                .add("nftAllowances", validNftAllowances)
                .toString();
    }
}
