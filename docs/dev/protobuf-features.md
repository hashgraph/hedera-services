# Development with new Hedera protobufs

:bangbang: **Links and branch names in this doc will change before the merge to `master`** :bangbang:

Services developers are the target audience for this document. It summarizes
the steps to take when developing a feature that changes the Hedera protobufs.

Suppose your feature is for issue number `123`. There are basically five steps to 
set up development for the feature:
 1. Create a `0123-D-DoThings` branch off `develop` in the [Hedera protobufs](https://github.com/hashgraph/hedera-protobufs) 
    repository; make your changes to the _*.proto_ in this branch.
 2. Create a `0123-M-DoThings` branch off `0883-M-V12ReleasePrep` in the [Hedera protobufs Java API](https://github.com/hashgraph/hedera-protobuf) 
    repository; copy the _*.proto_ from your `0123-D-DoThings` branch in the Hedera protobufs repo.
 3. Suppose the `version` element in the `master` _pom.xml_ above is `0.12.0-SNAPSHOT`. Then in 
    the `0123-M-DoThings` branch, change this to `0.12.0-issue.123-SNAPSHOT`.
 4. Make sure you have Sonatype OSS credentials in your _settings.xml_ as described [here](../release-automation.md). Then in 
    the [Hedera protobufs Java API](https://github.com/hashgraph/hedera-protobuf) repository, run `mvn -Prelease deploy`. 
    If this succeeds, you should see output like,
    ``` ...  Uploading to ossrh: https://oss.sonatype.org/content/repositories/snapshots/com/hedera/hashgraph/hedera-protobuf-java-api/0.12.0-issue.123-SNAPSHOT/hedera-protobuf-java-api-0.12.0-issue.123-20210202.205133-4.jar ... ```
 5. Finally, in your `0123-M-DoThings` branch in the main [Hedera Services](https://github.com/hashgraph/hedera-services) 
    repository (that is, _this_ repository), :exclamation: **make certain** you have merged the `0883-M-UseProtoArtifact` branch :exclamation:, and then 
    update the `hapi-proto.version` property to `0.12.0-issue.123-SNAPSHOT`.

At this point you should be able to commit your changes and push to GitHub, and CircleCI flows should run successfully.
