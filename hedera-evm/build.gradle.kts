plugins {
    id("com.hedera.hashgraph.hedera-conventions")
}

description = "Hedera Evm"

dependencies {
    implementation(project(":hapi-utils"))
    implementation(libs.bundles.besu)
    implementation(libs.bundles.logging)
    implementation(libs.commons.lang3)
    implementation(libs.hapi)
    implementation(libs.javax.inject)
    implementation(libs.jackson)
    implementation(libs.jetbrains.annotation)
    testImplementation(testLibs.bundles.testing)
}

