# Contributing Guidelines

Hedera Services accepts contributions via GitHub pull requests. 
This document outlines the process to help get your contribution accepted.

## Contents
- [Support Channels](#support-channels)
- [Issues](#issues)
  - [Vulnerability Disclosure](#vulnerability-disclosure)
  - [Types](#issue-types)
  - [Lifecycle](#issue-lifecycle)
- [Pull Requests](#pull-requests)
  - [Forking](#forking)
  - [Sign Off](#sign-off)
  - [Lifecycle](#pr-lifecycle)
- [Releases](#releases)

## Support Channels

Whether you are a user or contributor, official support channels include:

- [Issues](https://github.com/hashgraph/hedera-services/issues)
- [Discord](https://discordapp.com/invite/FFb9YFX)

Before opening a new issue or submitting a new pull request, it's helpful to search the project -
it's likely that another user has already reported the issue you're facing, or it's a known issue
that we're already aware of. It is also worth asking on the Discord channels.

## Issues

Issues are used as the primary method for tracking anything to do with the project.

### Vulnerability Disclosure

Most of the time, when you find a bug, it should be reported using
[GitHub issues](https://github.com/hashgraph/hedera-services/issues). However, if
you are reporting a _security vulnerability_, please email a report to
[security@hedera.com](mailto:security@hedera.com). This will give
us a chance to try to fix the issue before it is exploited in the wild.

### Issue Types

There are 3 types of issues (each with their own corresponding [label](https://github.com/hashgraph/hedera-services/labels)):

- **Bugs:** These track bugs with the code or problems with the documentation (i.e. missing or incomplete)
- **Features:** These track specific feature requests and ideas until they are complete. If the feature is
sufficiently large, complex or requires coordination among multiple Hedera projects, it should
first go through the [Hedera Improvement Proposal](https://github.com/hashgraph/hip) process.
- **Question:** These are support or functionality inquiries that we want to have a record of for
future reference. Generally these are questions that are too complex or large to store in the
Discord channel or have particular interest to the community as a whole. Depending on the discussion,
these can turn into a "Feature" or "Bug".

### Issue Lifecycle

The issue lifecycle is mainly driven by the core maintainers, but is good information for those
contributing. All issue types follow the same general lifecycle. Differences are noted below.

1. **Issue creation**
2. **Triage**
    - The maintainer in charge of triaging will apply the proper labels for the issue. This
      includes labels for priority and type.
    - Clean up the title to succinctly and clearly state the issue (if needed).
    - Add the issue to the correct milestone.
    - We attempt to do this process at least once per work day.
3. **Discussion**
    - "Feature" and "Bug" issues should be connected to the PR that resolves it.
    - Whoever is working on a "Feature" or "Bug" issue (whether a maintainer or someone from
      the community), should either assign the issue to them self or make a comment in the issue
      saying that they are taking it.
    - "Question" issues should stay open until resolved or if they have not been
      active for more than 30 days. This will help keep the issue queue to a manageable size and
      reduce noise.
4. **Issue closure**
    - Issues should be closed when a PR is merged or closed manually by the submitter or maintainer
      if it is determined that is not necessary.

## Pull Requests

Like most open source projects, we use Pull Requests (PRs) to track code changes.

### Forking

1. Fork the [hedera-services](https://github.com/hashgraph/hedera-services) repo

Go to the [project](https://github.com/hashgraph/hedera-services) page then hit the `Fork`
button to fork your own copy of the repository to your GitHub account.

2. Clone the forked repo to your local working directory.
```sh
$ git clone https://github.com/$your_github_account/hedera-services.git   
```
3. Add an `upstream` remote to keep your fork in sync with the main repo.
```sh
$ cd hedera-services
$ git remote add upstream https://github.com/hashgraph/hedera-services.git
$ git remote -v

origin  https://github.com/$your_github_account/hedera-services.git (fetch)
origin  https://github.com/$your_github_account/hedera-services.git (push)
upstream        https://github.com/hashgraph/hedera-services.git (fetch)
upstream        https://github.com/hashgraph/hedera-services.git (push)
```
4. Sync your local `master` branch.
```sh
$ git pull upstream master
```
5. Create a branch to add a new feature or fix issues.
```sh
$ git checkout -b new-feature
```
6. Make any change on the branch `new-feature` then build and test your codes.
7. Include in what will be committed.
```sh
$ git add <file>
```
8. Use [sign-off](#sign-off) when making each of your commits. If you forgot to sign some commits
that are part of the contribution, you can ask [git to rewrite your commit history](https://git-scm.com/book/en/v2/Git-Tools-Rewriting-History).
```sh
$ git commit --signoff -m "Your commit message"
```
9. [Submit](#pr-lifecycle) a pull request.

### Sign Off

The sign-off is a simple line at the end of commit message. All
commits needs to be signed. Your signature certifies that you wrote the code or
otherwise have the right to contribute the material. First, read the
[Developer Certificate of Origin](https://developercertificate.org/) (DCO) to
fully understand its terms.

Contributors sign-off that they adhere to these requirements by adding a Signed-off-by
line to commit messages (as seen via `git log`):

```
Author: Joe Smith <joe.smith@example.com>
Date:   Thu Feb 2 11:41:15 2018 -0800

    Update README

    Signed-off-by: Joe Smith <joe.smith@example.com>
```

Use your real name and email (sorry, no pseudonyms or anonymous contributions).
Notice the `Author` and `Signed-off-by` lines match. If they don't your PR will be
rejected by the automated DCO check.

If you set your `user.name` and `user.email` git configs, you can sign your
commit automatically with `-s` command line option:

```sh
$ git commit -s -m 'Update README'
```

### PR Lifecycle

Now that you've got your [forked](#forking) branch and [signed off](#sign-off) any commits, you can proceed to submit it.

1. **Submitting**
    - It is preferred, but not required, to have a PR tied to a specific issue. There can be
      circumstances where if it is a quick fix then an issue might be overkill. The details provided
      in the PR description would suffice in this case.
    - The PR description or commit message should contain a [keyword](https://help.github.com/en/articles/closing-issues-using-keywords)
      to automatically close the related issue.
    - Commits should be as small as possible, while ensuring that each commit is correct independently
      (i.e., each commit should compile and pass tests).
    - Add tests and documentation relevant to the fixed bug or new feature.
    - We more than welcome PRs that are currently in progress. If a PR is a work in progress,
      it should be opened as a [Draft PR](https://help.github.com/en/articles/about-pull-requests#draft-pull-requests).
      Once the PR is ready for review, mark it as ready to convert it to a regular PR.
2. **Triage**
    - The maintainer in charge of triaging will apply the proper labels for the issue.
    - Add the PR to the correct milestone. This should be the same as the issue the PR closes.
    - The maintainer can assign a reviewer or a reviewer can self assign themselves as a reviewer.
3. **Reviewing**
    - All reviews will be completed using the Github review tool.
    - A "Comment" review should be used when there are questions about the code that should be
      answered, but that don't involve code changes. This type of review does not count as approval.
    - A "Changes Requested" review indicates that changes to the code need to be made before they will be
      merged.
    - For documentation, special attention will be paid to spelling, grammar, and clarity
      (whereas those things don't matter *as* much for comments in code).
    - Reviews are also welcome from others in the community, especially those who have encountered a bug or
      have requested a feature. In the code review, a message can be added, as well as `LGTM` if the PR is
      good to merge. It’s also possible to add comments to specific lines in a file, for giving context
      to the comment.
    - PR owner should try to be responsive to comments by answering questions or changing code. If the
      owner is unsure of any comment, reach out to the person who added the comment in Discord. Once all comments
      have been addressed, the PR is ready to be merged.
4. **Merge or Close**
    - PRs should stay open until merged or be closed if the submitter has not been responsive for more than 30 days.
      This will help keep the PR queue to a manageable size and reduce noise.

## Releases

Hedera uses [Semantic Versioning](https://semver.org) for releases to convey meaning about the
underlying code and what has been modified from one version to the next. We use milestones to
track the progress of a release. Assigning issues to a milestone is on a best effort basis and
we make no guarantees as to when a particular issue will be released. A milestone (and hence
release) is considered done when all outstanding issues/PRs have been closed or moved to another
milestone.

