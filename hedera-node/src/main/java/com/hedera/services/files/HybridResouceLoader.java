package com.hedera.services.files;

import com.hedera.services.utils.EntityIdUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.units.qual.N;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.regex.Pattern;

import static com.hedera.services.utils.EntityIdUtils.asFile;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;

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
        try (final var in = HybridResouceLoader.class .getClassLoader() .getResourceAsStream(resourceLoc)) {
            if (null == in) {
                throw new IOException(
                        "Could not load resource '" + resourceLoc + "'");
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
