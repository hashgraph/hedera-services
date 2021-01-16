package com.hedera.services.store;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * A summary of the result of trying to create a store member, such as a token or scheduled entity.
 *
 * @param <T> the type of the store member.
 */
public class CreationResult<T> {
    private final ResponseCodeEnum status;
    private final Optional<T> created;

    private CreationResult(
            ResponseCodeEnum status,
            Optional<T> created
    ) {
        this.status = status;
        this.created = created;
    }

    /**
     * Factory to summarize the result of a failed store member creation.
     *
     * @param type the kind of failure that occurred.
     * @param <T> the type of store member being created.
     * @return the summary constructed.
     */
    public static <T> CreationResult<T> failure(ResponseCodeEnum type) {
        return new CreationResult<>(type, Optional.empty());
    }

    /**
     * Factory to summarize the result of a successful store member creation.
     *
     * @param created the resulting store member.
     * @param <T> the type of store member being created.
     * @return the summary constructed.
     */
    public static <T> CreationResult<T> success(T created) {
        return new CreationResult<>(OK, Optional.of(created));
    }

    public ResponseCodeEnum getStatus() {
        return status;
    }

    public Optional<T> getCreated() {
        return created;
    }
}
