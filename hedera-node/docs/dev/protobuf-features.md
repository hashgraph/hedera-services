# Development with new Hedera protobufs

In this note we summarize the procedure for developing a Services
feature that requires changing the Hedera protobufs. There are 
two main topics: (1) setting up for development, and (2) opening 
PRs when development is complete.

At least some of these steps will be automated over time, but the 
process as is shouldn't be overly burdensome as long as the issue
is reasonably scoped and the protobuf changes are designed carefully
at the beginning of the issue.

# Setup

Suppose your feature is for issue number `123`. Follow these steps to set up and 
iterate development of the feature.
 1. Create a `0123-D-DoThings` branch off `develop` in the [Hedera protobufs](https://github.com/hashgraph/hedera-protobufs) 
    repository; make your changes to the _*.proto_ in this branch.
 2. Create a `0123-M-DoThings` branch off `master` in the [Hedera protobufs Java](https://github.com/hashgraph/hedera-protobufs-java) 
    repository; sync your protobuf changes to this branch and use it to deploy 
    snapshot versions of the `com.hedera.hashgraph:hedera-protobuf-java-api` artifact
    to the OSSRH snapshot repository. 
    - In this branch, change the `version` element in the _pom.xml_ to reflect your issue.
      For example, if the working version is `0.12.0-SNAPSHOT`, change it to `0.12.0-issue.123-SNAPSHOT`.
    - Ensure you have valid Sonatype OSS credentials in your _settings.xml_ as described [here](../release-automation.md). 
    - Each time you change the protobuf in the Hedera protobufs repo and sync to the Hedera protobufs Java repo, 
      run `mvn deploy` here to update the snapshot artifact for your feature.
 3. Finally, in your `0123-M-DoThings` branch in the [Hedera Services](https://github.com/hashgraph/hedera-services) 
    repository (that is, _this_ repository), update the `hapi-proto.version` property in the top-level _pom.xml_ 
    to `0.12.0-issue.123-SNAPSHOT`. This allows your branch to build in CircleCI.

# Opening PRs

You should open and complete PRs in the same order you created the branches above.
That is,
 1. Open your PR to `develop` in the [Hedera protobufs](https://github.com/hashgraph/hedera-protobufs) 
    repo. 
 2. _Only when_ the PR from (1) is approved and merged, open a PR containing the identical _*.proto_ changes to 
    `master` in the [Hedera protobufs Java](https://github.com/hashgraph/hedera-protobufs-java). (Note
    the pull request template in this repo contains a single checkbox requesting a link to the closed
    PR in the upstream Hedera protobufs repo.)
    - In this PR, revert the `version` element in the _pom.xml_ to no longer refer to your issue.
 3. When the PR from (2) is approved and merged, run `mvn deploy` from `master` in the 
    [Hedera protobufs Java](https://github.com/hashgraph/hedera-protobufs-java) repo to 
    deploy the new working version of the protobufs to the OSSRH snapshot repository.
 4. _Only when_ (3) is complete, open your PR in [Hedera Services](https://github.com/hashgraph/hedera-services) 
    to `master`. Similarly to (2), ensure the `hapi-proto.version` property in the top-level
    _pom.xml_ no longer refers to your branch.


