/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.test.fixtures;

import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableSingletonStateBase;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableSingletonStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;

public class StateTestBase extends TestBase {
    protected static final String UNKNOWN_STATE_KEY = "BOGUS_STATE_KEY";
    protected static final String UNKNOWN_KEY = "BOGUS_KEY";

    protected static final int FRUIT_STATE_ID = 123;
    protected static final int ANIMAL_STATE_ID = 234;
    protected static final int COUNTRY_STATE_ID = 345;
    protected static final int STEAM_STATE_ID = 456;
    protected static final String FRUIT_STATE_KEY = "FRUIT";
    protected static final String ANIMAL_STATE_KEY = "ANIMAL";
    protected static final String SPACE_STATE_KEY = "SPACE";
    protected static final String STEAM_STATE_KEY = "STEAM";
    public static final String COUNTRY_STATE_KEY = "COUNTRY";

    protected static final String A_KEY = "A";
    protected static final String B_KEY = "B";
    protected static final String C_KEY = "C";
    protected static final String D_KEY = "D";
    protected static final String E_KEY = "E";
    protected static final String F_KEY = "F";
    protected static final String G_KEY = "G";

    protected static final StringRecord APPLE = new StringRecord("Apple");
    protected static final StringRecord ACAI = new StringRecord("Acai");
    protected static final StringRecord BANANA = new StringRecord("Banana");
    protected static final StringRecord BLACKBERRY = new StringRecord("BlackBerry");
    protected static final StringRecord BLUEBERRY = new StringRecord("BlueBerry");
    protected static final StringRecord CHERRY = new StringRecord("Cherry");
    protected static final StringRecord CRANBERRY = new StringRecord("Cranberry");
    protected static final StringRecord DATE = new StringRecord("Date");
    protected static final StringRecord DRAGONFRUIT = new StringRecord("DragonFruit");
    protected static final StringRecord EGGPLANT = new StringRecord("Eggplant");
    protected static final StringRecord ELDERBERRY = new StringRecord("ElderBerry");
    protected static final StringRecord FIG = new StringRecord("Fig");
    protected static final StringRecord FEIJOA = new StringRecord("Feijoa");
    protected static final StringRecord GRAPE = new StringRecord("Grape");

    protected static final StringRecord AARDVARK = new StringRecord("Aardvark");
    protected static final StringRecord BEAR = new StringRecord("Bear");
    protected static final StringRecord CUTTLEFISH = new StringRecord("Cuttlefish");
    protected static final StringRecord DOG = new StringRecord("Dog");
    protected static final StringRecord EMU = new StringRecord("Emu");
    protected static final StringRecord FOX = new StringRecord("Fox");
    protected static final StringRecord GOOSE = new StringRecord("Goose");

    protected static final String ASTRONAUT = "Astronaut";
    protected static final String BLASTOFF = "Blastoff";
    protected static final String COMET = "Comet";
    protected static final String DRACO = "Draco";
    protected static final String EXOPLANET = "Exoplanet";
    protected static final String FORCE = "Force";
    protected static final String GRAVITY = "Gravity";

    protected static final String ART = "Art";
    protected static final String BIOLOGY = "Biology";
    protected static final String CHEMISTRY = "Chemistry";
    protected static final String DISCIPLINE = "Discipline";
    protected static final String ECOLOGY = "Ecology";
    protected static final String FIELDS = "Fields";
    protected static final String GEOMETRY = "Geometry";

    protected static final String AUSTRALIA = "Australia";
    protected static final String BRAZIL = "Brazil";
    protected static final String CHAD = "Chad";
    protected static final String DENMARK = "Denmark";
    protected static final String ESTONIA = "Estonia";
    protected static final String FRANCE = "France";
    protected static final String GHANA = "Ghana";

    @NonNull
    protected MapReadableKVState<String, StringRecord> readableFruitState() {
        return MapReadableKVState.<String, StringRecord>builder(FRUIT_STATE_KEY)
                .value(A_KEY, APPLE)
                .value(B_KEY, BANANA)
                .value(C_KEY, CHERRY)
                .value(D_KEY, DATE)
                .value(E_KEY, EGGPLANT)
                .value(F_KEY, FIG)
                .value(G_KEY, GRAPE)
                .build();
    }

    @NonNull
    protected MapWritableKVState<String, StringRecord> writableFruitState() {
        return MapWritableKVState.<String, StringRecord>builder(FRUIT_STATE_KEY)
                .value(A_KEY, APPLE)
                .value(B_KEY, BANANA)
                .value(C_KEY, CHERRY)
                .value(D_KEY, DATE)
                .value(E_KEY, EGGPLANT)
                .value(F_KEY, FIG)
                .value(G_KEY, GRAPE)
                .build();
    }

    @NonNull
    protected MapReadableKVState<String, StringRecord> readableAnimalState() {
        return MapReadableKVState.<String, StringRecord>builder(ANIMAL_STATE_KEY)
                .value(A_KEY, AARDVARK)
                .value(B_KEY, BEAR)
                .value(C_KEY, CUTTLEFISH)
                .value(D_KEY, DOG)
                .value(E_KEY, EMU)
                .value(F_KEY, FOX)
                .value(G_KEY, GOOSE)
                .build();
    }

    @NonNull
    protected MapWritableKVState<String, StringRecord> writableAnimalState() {
        return MapWritableKVState.<String, StringRecord>builder(ANIMAL_STATE_KEY)
                .value(A_KEY, AARDVARK)
                .value(B_KEY, BEAR)
                .value(C_KEY, CUTTLEFISH)
                .value(D_KEY, DOG)
                .value(E_KEY, EMU)
                .value(F_KEY, FOX)
                .value(G_KEY, GOOSE)
                .build();
    }

    @NonNull
    protected ReadableSingletonState<String> readableSpaceState() {
        return new ReadableSingletonStateBase<>(SPACE_STATE_KEY, () -> ASTRONAUT);
    }

    @NonNull
    protected WritableSingletonState<String> writableSpaceState() {
        final AtomicReference<String> backingValue = new AtomicReference<>(ASTRONAUT);
        return new WritableSingletonStateBase<>(SPACE_STATE_KEY, backingValue::get, backingValue::set);
    }

    @NonNull
    protected ListReadableQueueState<String> readableSTEAMState() {
        return ListReadableQueueState.<String>builder(STEAM_STATE_KEY)
                .value(ART)
                .value(BIOLOGY)
                .value(CHEMISTRY)
                .value(DISCIPLINE)
                .value(ECOLOGY)
                .value(FIELDS)
                .value(GEOMETRY)
                .build();
    }

    @NonNull
    protected ListWritableQueueState<String> writableSTEAMState() {
        return ListWritableQueueState.<String>builder(STEAM_STATE_KEY)
                .value(ART)
                .value(BIOLOGY)
                .value(CHEMISTRY)
                .value(DISCIPLINE)
                .value(ECOLOGY)
                .value(FIELDS)
                .value(GEOMETRY)
                .build();
    }

    @NonNull
    protected ReadableSingletonState<String> readableCountryState() {
        return new ReadableSingletonStateBase<>(COUNTRY_STATE_KEY, () -> AUSTRALIA);
    }

    @NonNull
    protected WritableSingletonState<String> writableCountryState() {
        final AtomicReference<String> backingValue = new AtomicReference<>(AUSTRALIA);
        return new WritableSingletonStateBase<>(COUNTRY_STATE_KEY, backingValue::get, backingValue::set);
    }
}
