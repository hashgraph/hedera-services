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

package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import static com.hedera.services.bdd.junit.hedera.ExternalPath.UPGRADE_ARTIFACTS_DIR;
import static com.hedera.services.bdd.junit.hedera.subprocess.ProcessUtils.conditionFuture;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.CONFIG_TXT;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.loadAddressBook;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.spec.utilops.lifecycle.AbstractLifecycleOp;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigTxtValidationOp extends AbstractLifecycleOp {
    private static final Logger log = LogManager.getLogger(ConfigTxtValidationOp.class);

    private static final Duration CONFIG_TXT_TIMEOUT = Duration.ofSeconds(10);
    private final Consumer<AddressBook> bookValidator;

    public ConfigTxtValidationOp(
            @NonNull final NodeSelector selector, @NonNull final Consumer<AddressBook> bookValidator) {
        super(selector);
        this.bookValidator = Objects.requireNonNull(bookValidator);
    }

    @Override
    protected void run(@NonNull final HederaNode node) {
        final var configTxtPath = node.getExternalPath(UPGRADE_ARTIFACTS_DIR).resolve(CONFIG_TXT);
        final AtomicReference<String> lastFailure = new AtomicReference<>();
        log.info("Validating address book at {}", configTxtPath);
        try {
            conditionFuture(() -> containsLoadableAddressBook(configTxtPath, lastFailure::set))
                    .get(CONFIG_TXT_TIMEOUT.toMillis(), MILLISECONDS);
        } catch (Exception e) {
            log.error("Unable to validate address book from {} (last error='{}')", configTxtPath, lastFailure.get(), e);
            throw new IllegalStateException(e);
        }
        final var addressBook = loadAddressBook(configTxtPath);
        bookValidator.accept(addressBook);
    }

    private boolean containsLoadableAddressBook(
            @NonNull final Path configTxtPath, @NonNull final Consumer<String> lastError) {
        try {
            log.info("Attempting to load address book from {}", configTxtPath);
            final var addressBook = loadAddressBook(configTxtPath);
            log.info("  -> Loaded book from {} with {} entries", configTxtPath, addressBook.getSize());
            return true;
        } catch (Throwable t) {
            lastError.accept(t.getClass().getSimpleName() + " - '" + t.getMessage() + "'");
            log.warn("Unable to load address book from {}", configTxtPath, t);
            return false;
        }
    }
}
