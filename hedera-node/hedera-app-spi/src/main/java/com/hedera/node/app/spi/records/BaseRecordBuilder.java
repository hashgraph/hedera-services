/*
<<<<<<<< HEAD:hedera-node/hedera-app/src/main/java/com/hedera/node/app/annotations/NodeSelfId.java
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
========
 * Copyright (C) 2023 Hedera Hashgraph, LLC
>>>>>>>> develop:hedera-node/hedera-app-spi/src/main/java/com/hedera/node/app/spi/records/BaseRecordBuilder.java
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

<<<<<<<< HEAD:hedera-node/hedera-app/src/main/java/com/hedera/node/app/annotations/NodeSelfId.java
package com.hedera.node.app.annotations;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

/**
 * This annotation is used with dependency injection to inject the node's self account ID as an argument to a
 * class constructor.
 */
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Qualifier
@Retention(RUNTIME)
public @interface NodeSelfId {}
========
package com.hedera.node.app.spi.records;

/**
 * Base implementation of a {@code UniversalRecordBuilder} that will track all the
 * "universal" transaction metadata and side effects. This builder is used when there are no
 * side effects to record from the transaction(e.g. a token pause).
 */
public class BaseRecordBuilder<T extends RecordBuilder<T>> extends UniversalRecordBuilder<T> {
    /**
     * {@inheritDoc}
     */
    @Override
    public T self() {
        return (T) this;
    }
}
>>>>>>>> develop:hedera-node/hedera-app-spi/src/main/java/com/hedera/node/app/spi/records/BaseRecordBuilder.java
