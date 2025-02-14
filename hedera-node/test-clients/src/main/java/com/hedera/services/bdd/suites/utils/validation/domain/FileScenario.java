// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.suites.utils.validation.domain;

public class FileScenario {
    public static String NOVEL_FILE_NAME = "novelFile";
    public static String PERSISTENT_FILE_NAME = "persistentFile";
    public static String DEFAULT_CONTENTS_RESOURCE = "validation-scenarios/MrBleaney.txt";

    PersistentFile persistent;

    public PersistentFile getPersistent() {
        return persistent;
    }

    public void setPersistent(PersistentFile persistent) {
        this.persistent = persistent;
    }
}
