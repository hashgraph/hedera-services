/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.platform;

import com.hedera.node.app.annotations.CommonExecutor;
import com.hedera.node.app.service.mono.utils.JvmSystemExits;
import com.hedera.node.app.service.mono.utils.NamedDigestFactory;
import com.hedera.node.app.service.mono.utils.SystemExits;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.stream.Signer;
import com.swirlds.platform.system.Platform;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Module
public interface PlatformModule {
    @Provides
    @Singleton
    static NodeId selfId(@NonNull final Platform platform) {
        return platform.getSelfId();
    }

    @Provides
    @Singleton
    static Signer signer(@NonNull final Platform platform) {
        return platform::sign;
    }

    @Provides
    @Singleton
    @CommonExecutor
    static ExecutorService provideCommonExecutor() {
        return ForkJoinPool.commonPool();
    }

    @Binds
    @Singleton
    SystemExits bindSystemExits(JvmSystemExits systemExits);

    @Provides
    @Singleton
    static Supplier<Charset> provideNativeCharset() {
        return Charset::defaultCharset;
    }

    @Provides
    @Singleton
    static NamedDigestFactory provideDigestFactory() {
        return MessageDigest::getInstance;
    }
}
