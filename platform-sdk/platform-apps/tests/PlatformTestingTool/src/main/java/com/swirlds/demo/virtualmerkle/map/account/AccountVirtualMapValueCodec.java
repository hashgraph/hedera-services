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

package com.swirlds.demo.virtualmerkle.map.account;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

public class AccountVirtualMapValueCodec implements Codec<AccountVirtualMapValue> {

    public static final AccountVirtualMapValueCodec INSTANCE = new AccountVirtualMapValueCodec();

    @NonNull
    @Override
    public AccountVirtualMapValue parse(@NonNull ReadableSequentialData in, boolean strictMode, int maxDepth)
            throws ParseException {
        return new AccountVirtualMapValue(in);
    }

    @Override
    public void write(@NonNull AccountVirtualMapValue value, @NonNull WritableSequentialData out) throws IOException {
        value.writeTo(out);
    }

    @Override
    public int measure(@NonNull ReadableSequentialData in) throws ParseException {
        throw new UnsupportedOperationException("AccountVirtualMapValueCodec.measure() not implemented");
    }

    @Override
    public int measureRecord(AccountVirtualMapValue value) {
        return value.getSizeInBytes();
    }

    @Override
    public boolean fastEquals(@NonNull AccountVirtualMapValue value, @NonNull ReadableSequentialData in)
            throws ParseException {
        final AccountVirtualMapValue other = parse(in, true, Integer.MAX_VALUE);
        return Objects.equals(value, other);
    }
}
