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

package com.hedera.services.bdd.spec.utilops.inventory;

import static com.hedera.node.app.hapi.utils.keys.Ed25519Utils.readKeyPairFrom;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.status.StatusConsoleListener;
import org.apache.logging.log4j.status.StatusLogger;

public class AccessoryUtils {
    private AccessoryUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static Optional<File> keyFileAt(String sansExt) {
        var pemFile = Paths.get(sansExt + ".pem").toFile();
        if (pemFile.exists()) {
            return Optional.of(pemFile);
        }

        var wordsFile = Paths.get(sansExt + ".words").toFile();
        if (wordsFile.exists()) {
            return Optional.of(wordsFile);
        }

        var hexedFile = Paths.get(sansExt + ".hex").toFile();
        if (hexedFile.exists()) {
            return Optional.of(hexedFile);
        }

        return Optional.empty();
    }

    public static boolean isValid(File keyFile, Optional<String> passphrase) {
        return passphrase.isPresent() && unlocksPem(keyFile, passphrase.get());
    }

    public static Optional<File> passFileFor(File pemFile) {
        var absPath = pemFile.getAbsolutePath();
        var passFile = new File(absPath.replace(".pem", ".pass"));
        return passFile.exists() ? Optional.of(passFile) : Optional.empty();
    }

    public static void setLogLevels(Level logLevel, @NonNull final List<Class<?>> suites) {
        final var statusLogger = StatusLogger.getLogger();
        statusLogger.registerListener(new StatusConsoleListener(logLevel));
        suites.forEach(cls -> setLogLevel(cls, logLevel));
    }

    public static Optional<String> promptForPassphrase(
            @NonNull final String loc,
            @NonNull final String prompt,
            int maxAttempts,
            @NonNull final BiPredicate<File, String> isUnlocked) {
        final var f = new File(loc);
        String fullPrompt = prompt + ": ";
        char[] passphrase;
        while (maxAttempts-- > 0) {
            passphrase = readCandidate(fullPrompt);
            final var asString = new String(passphrase);
            if (isUnlocked.test(f, asString)) {
                return Optional.of(asString);
            } else {
                if (maxAttempts > 0) {
                    System.out.println(
                            "Sorry, that isn't it! (Don't worry, still " + maxAttempts + " attempts remaining.)");
                } else {
                    return Optional.empty();
                }
            }
        }
        throw new AssertionError("Impossible!");
    }

    public static Optional<String> promptForPassphrase(String pemLoc, String prompt, int maxAttempts) {
        return promptForPassphrase(pemLoc, prompt, maxAttempts, AccessoryUtils::unlocksPem);
    }

    public static boolean unlocksPem(File keyFile, String passphrase) {
        try {
            readKeyPairFrom(keyFile, passphrase);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static void setLogLevel(Class<?> cls, Level logLevel) {
        ((org.apache.logging.log4j.core.Logger) LogManager.getLogger(cls)).setLevel(logLevel);
    }

    private static char[] readCandidate(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        if (System.console() != null) {
            return System.console().readPassword();
        } else {
            var reader = new BufferedReader(new InputStreamReader(System.in));
            try {
                return reader.readLine().toCharArray();
            } catch (IOException e) {
                return new char[0];
            }
        }
    }
}
