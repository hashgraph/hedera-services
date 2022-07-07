/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_THROTTLE_DEFINITIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNPARSEABLE_THROTTLE_DEFINITIONS;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.files.FileUpdateInterceptor;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.sysfiles.validation.ErrorCodeUtils;
import com.hedera.services.sysfiles.validation.ExpectedCustomThrottles;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import com.swirlds.common.system.address.AddressBook;
import java.util.AbstractMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ThrottleDefsManager implements FileUpdateInterceptor {
    static final Map.Entry<ResponseCodeEnum, Boolean> YES_VERDICT =
            new AbstractMap.SimpleImmutableEntry<>(SUCCESS, true);
    static final Map.Entry<ResponseCodeEnum, Boolean> YES_BUT_MISSING_OP_VERDICT =
            new AbstractMap.SimpleImmutableEntry<>(SUCCESS_BUT_MISSING_EXPECTED_OPERATION, true);
    static final Map.Entry<ResponseCodeEnum, Boolean> UNPARSEABLE_VERDICT =
            new AbstractMap.SimpleImmutableEntry<>(UNPARSEABLE_THROTTLE_DEFINITIONS, false);
    private static final Map.Entry<ResponseCodeEnum, Boolean> DEFAULT_INVALID_VERDICT =
            new AbstractMap.SimpleImmutableEntry<>(INVALID_THROTTLE_DEFINITIONS, false);

    static final int APPLICABLE_PRIORITY = 0;

    Set<HederaFunctionality> expectedOps = ExpectedCustomThrottles.ACTIVE_OPS;

    private final FileNumbers fileNums;
    private final Supplier<AddressBook> addressBook;
    private final Consumer<ThrottleDefinitions> postUpdateCb;

    Function<
                    ThrottleDefinitions,
                    com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions>
            toPojo = com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions::fromProto;

    public ThrottleDefsManager(
            FileNumbers fileNums,
            Supplier<AddressBook> addressBook,
            Consumer<ThrottleDefinitions> postUpdateCb) {
        this.fileNums = fileNums;
        this.addressBook = addressBook;
        this.postUpdateCb = postUpdateCb;
    }

    @Override
    public OptionalInt priorityForCandidate(FileID id) {
        return isThrottlesDef(id) ? OptionalInt.of(APPLICABLE_PRIORITY) : OptionalInt.empty();
    }

    @Override
    public Map.Entry<ResponseCodeEnum, Boolean> preUpdate(FileID id, byte[] newContents) {
        Optional<ThrottleDefinitions> newThrottles = uncheckedParseFrom(newContents);
        if (newThrottles.isEmpty()) {
            return UNPARSEABLE_VERDICT;
        }

        var n = addressBook.get().getSize();
        var proto = newThrottles.get();
        var defs = toPojo.apply(proto);
        Set<HederaFunctionality> customizedOps = new HashSet<>();
        for (var bucket : defs.getBuckets()) {
            try {
                bucket.asThrottleMapping(n);
            } catch (Exception e) {
                var detailError = ErrorCodeUtils.errorFrom(e.getMessage());
                return detailError
                        .<Map.Entry<ResponseCodeEnum, Boolean>>map(
                                code -> new AbstractMap.SimpleImmutableEntry<>(code, false))
                        .orElse(DEFAULT_INVALID_VERDICT);
            }
            for (var group : bucket.getThrottleGroups()) {
                customizedOps.addAll(group.getOperations());
            }
        }

        return expectedOps.equals(EnumSet.copyOf(customizedOps))
                ? YES_VERDICT
                : YES_BUT_MISSING_OP_VERDICT;
    }

    @Override
    public void postUpdate(FileID id, byte[] contents) {
        /* Note - here we trust the file system to correctly invoke this interceptor
        only when we returned a priority from {@code priorityForCandidate}. */
        final Optional<ThrottleDefinitions> throttleDefinitions = uncheckedParseFrom(contents);
        throttleDefinitions.ifPresent(postUpdateCb);
    }

    @Override
    public Map.Entry<ResponseCodeEnum, Boolean> preDelete(FileID id) {
        throw new UnsupportedOperationException("Cannot delete the throttle definitions file!");
    }

    @Override
    public Map.Entry<ResponseCodeEnum, Boolean> preAttrChange(FileID id, HFileMeta newAttr) {
        return YES_VERDICT;
    }

    private boolean isThrottlesDef(FileID fid) {
        return fid.getFileNum() == fileNums.throttleDefinitions();
    }

    private Optional<ThrottleDefinitions> uncheckedParseFrom(byte[] data) {
        try {
            return Optional.of(ThrottleDefinitions.parseFrom(data));
        } catch (InvalidProtocolBufferException ignore) {
            return Optional.empty();
        }
    }
}
