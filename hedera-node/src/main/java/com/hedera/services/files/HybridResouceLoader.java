/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.files;

import static com.hedera.services.utils.EntityIdUtils.asFile;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;

import java.io.IOException;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper that loads a resource as from <i>either</i> an asset in a JAR or from the Hedera file
 * system. The choice is based on whether the {@code resourceLoc} matches the {@code 0.0.X} pattern.
 */
@Singleton
public class HybridResouceLoader {
    private static final Pattern HFS_RESOURCE_PATTERN = Pattern.compile("\\d+[.]\\d+[.]\\d+");
    private static final Logger log = LogManager.getLogger(HybridResouceLoader.class);
    private final TieredHederaFs hfs;

    @Inject
    public HybridResouceLoader(final TieredHederaFs hfs) {
        this.hfs = hfs;
    }

    @Nullable
    public byte[] readAllBytesIfPresent(final String resourceLoc) {
        if (HFS_RESOURCE_PATTERN.matcher(resourceLoc).matches()) {
            return readAllHfsResourceBytes(resourceLoc);
        } else {
            return readAllJarResourceBytes(resourceLoc);
        }
    }

    @Nullable
    private byte[] readAllJarResourceBytes(final String resourceLoc) {
        try (final var in =
                HybridResouceLoader.class.getClassLoader().getResourceAsStream(resourceLoc)) {
            if (null == in) {
                throw new IOException("Could not load resource '" + resourceLoc + "'");
            }
            return in.readAllBytes();
        } catch (IOException unavailable) {
            log.warn("Unable to read JAR resource", unavailable);
            return null;
        }
    }

    @Nullable
    private byte[] readAllHfsResourceBytes(final String resourceLoc) {
        try {
            final var fid = asFile(parseAccount(resourceLoc));
            return hfs.cat(fid);
        } catch (IllegalArgumentException unavailable) {
            log.warn("Unable to read HRS resource", unavailable);
            return null;
        }
    }
}
