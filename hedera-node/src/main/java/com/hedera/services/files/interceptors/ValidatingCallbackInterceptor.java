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
package com.hedera.services.files.interceptors;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.files.FileUpdateInterceptor;
import com.hedera.services.files.HFileMeta;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.AbstractMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ValidatingCallbackInterceptor implements FileUpdateInterceptor {
    static final Map.Entry<ResponseCodeEnum, Boolean> YES_VERDICT =
            new AbstractMap.SimpleImmutableEntry<>(SUCCESS, true);

    private final int applicablePriority;
    private final String fileNumProperty;
    private final PropertySource properties;
    private final Consumer<byte[]> postUpdateCb;
    private final Predicate<byte[]> validator;

    private boolean initialized = false;
    private long fileNum = -1;

    public ValidatingCallbackInterceptor(
            int applicablePriority,
            String fileNumProperty,
            PropertySource properties,
            Consumer<byte[]> postUpdateCb,
            Predicate<byte[]> validator) {
        this.applicablePriority = applicablePriority;
        this.fileNumProperty = fileNumProperty;
        this.properties = properties;
        this.postUpdateCb = postUpdateCb;
        this.validator = validator;
    }

    @Override
    public OptionalInt priorityForCandidate(FileID id) {
        lazyInitIfNecessary();

        return (id.getFileNum() == fileNum)
                ? OptionalInt.of(applicablePriority)
                : OptionalInt.empty();
    }

    @Override
    public Map.Entry<ResponseCodeEnum, Boolean> preUpdate(FileID id, byte[] newContents) {
        return YES_VERDICT;
    }

    @Override
    public void postUpdate(FileID id, byte[] contents) {
        lazyInitIfNecessary();

        if (priorityForCandidate(id).isPresent() && validator.test(contents)) {
            postUpdateCb.accept(contents);
        }
    }

    @Override
    public Map.Entry<ResponseCodeEnum, Boolean> preDelete(FileID id) {
        return YES_VERDICT;
    }

    @Override
    public Map.Entry<ResponseCodeEnum, Boolean> preAttrChange(FileID id, HFileMeta newAttr) {
        return YES_VERDICT;
    }

    private void lazyInitIfNecessary() {
        if (!initialized) {
            fileNum = properties.getLongProperty(fileNumProperty);
            initialized = true;
        }
    }
}
