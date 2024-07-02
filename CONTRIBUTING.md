# Contributing Guidelines

Hedera Hashgraph is grateful for any contributions it receives from the community. Contributions can come in many shapes
and forms including bug reports, code, answering questions on Discord, etc. This document outlines the process to help
get your contribution accepted.

## Questions

If you have a question on how to use the product, please see our [Support](/SUPPORT.md) guide.

## Issues

GitHub [issues](https://github.com/hashgraph/hedera-services/issues) are used as the primary method for tracking project changes. Issues
should track actionable items that will result in changes to the codebase. As a result, support inquiries should be
directed to one of the aforementioned [support channels](#support-channels).

### Vulnerability Disclosure

Most of the time, when you find a bug, it should be reported the GitHub issue tracker for the project. However, if you
are reporting a _security vulnerability_, please see our [Hedera bug bounty program](https://hedera.com/bounty).

### Issue Types

There are three types of issues each with their own corresponding label:

- **Bug:** These track issues with the code
- **Documentation:** These track problems or insufficient coverage with the documentation
- **Enhancement:** These track specific feature requests and ideas until they are complete. This should only be for
  trivial or minor enhancements. If the feature is sufficiently large, complex or requires coordination among multiple
  Hedera projects, it should first go through the
  [Hedera Improvement Proposal](https://github.com/hashgraph/hedera-improvement-proposal) process.

### Issue Lifecycle

The issue lifecycle is mainly driven by the core maintainers, but is still useful to know for those wanting to
contribute. All issue types follow the same general lifecycle. Differences will be noted below.

#### Creation

- The user will open a ticket in the GitHub project and apply one of the [issue type](#issue-types) labels.

#### Triage

- The maintainer in charge of triaging will apply the proper labels for the issue. This includes labels for priority and
  area.
- Clean up the title to succinctly and clearly state the issue (if needed).
- Ask the submitter to clarify any items.
- We attempt to do this process at least once per work day.

#### Discussion

- Issues should be connected to the [pull request](#pull-requests) that resolves it.
- Whoever is working on an issue (whether a maintainer or someone from the community), should either assign the issue to
  themself or add a comment that they will be working on it.

#### Closure

- Linked issues should be automatically closed when a PR is merged or closed manually by the submitter or maintainer if
  it is determined that is not necessary.

## Pull Requests

Like most open source projects, we use
[pull requests](https://docs.github.com/en/github/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests) (
PRs) to track code changes.    
Check out our [template](https://github.com/hashgraph/.github/blob/main/PULL_REQUEST_TEMPLATE.md) for a good starting point.

### Forking

1. [Fork](https://guides.github.com/activities/forking/) the repository. Go to the project page then hit the `Fork`
   button to fork your own copy of the repository to your GitHub account.

2. Clone the forked repository to your local working directory.

```sh
$ git clone https://github.com/${owner}/hedera-services.git
```

3. Create a branch to add a new feature or fix issues.

```sh
$ git checkout -b new-feature
```

4. Make any change on the branch `new-feature` then build and test your code locally.

5. Add files that you want to be committed.

```sh
$ git add <file>
```

6. Enable [GPG signing](https://docs.github.com/en/github/authenticating-to-github/managing-commit-signature-verification/signing-commits) of your commits within the repo.
   Signing all your commits with your public key allows the comunity to verify it's really you. If you forgot to sign some
   commits that are part of the contribution, you can
   ask [git to rewrite your commit history](https://git-scm.com/book/en/v2/Git-Tools-Rewriting-History).

```sh
$ git config commit.gpgsign true
```

7. Use [sign-off](#sign-off) when making each of your commits. Additionally,
   please [GPG sign](https://docs.github.com/en/github/authenticating-to-github/managing-commit-signature-verification/signing-commits)
   all your commits with your public key so we can verify it's really you. If you forgot to sign-off some
   commits that are part of the contribution, you can
   ask [git to rewrite your commit history](https://git-scm.com/book/en/v2/Git-Tools-Rewriting-History).

```sh
$ git commit --signoff -S -m "Your commit message"
```

8. [Submit](#pr-lifecycle) a pull request.

### Sign Off

The sign-off is a simple line at the end of a commit message. All commits needs to be signed. Your signature certifies
that you wrote the code or otherwise have the right to contribute the material. First, read the
[Developer Certificate of Origin](https://developercertificate.org/) (DCO) to fully understand its terms.

Contributors sign-off that they adhere to these requirements by adding a `Signed-off-by` line to commit messages (as
seen via `git log`):

```
Author: Joe Smith <joe.smith@example.com>
Date:   Thu Feb 2 11:41:15 2018 -0800

    Update README

    Signed-off-by: Joe Smith <joe.smith@example.com>
```

Use your real name and email (sorry, no pseudonyms or anonymous contributions). Notice the `Author` and `Signed-off-by`
lines match. If they don't your PR will be rejected by the automated DCO check.

If you set your `user.name` and `user.email` git configs, you can sign your commit automatically with `-s`
or `--sign-off` command line option:

```sh
$ git config --global user.name "Joe Smith"
$ git config --global user.email "joe.smith@example.com"
$ git commit -s -m 'Update README'
```

### PR Lifecycle

Now that you've got your [forked](#forking) branch, you can proceed to submit it.

#### Submitting

- It is preferred, but not required, to have a PR tied to a specific issue. There can be circumstances where if it is a
  quick fix then an issue might be overkill. The details provided in the PR description would suffice in this case.
- The PR description or commit message should contain
  a [keyword](https://help.github.com/en/articles/closing-issues-using-keywords)
  to automatically close the related issue.
- Commits should be as small as possible, while ensuring that each commit is correct independently
  (i.e., each commit should compile and pass tests).
- Add tests and documentation relevant to the fixed bug or new feature. Code coverage should stay the same or increase
  for the PR to be approved.
- We more than welcome PRs that are currently in progress. If a PR is a work in progress, it should be opened as
  a [Draft PR](https://help.github.com/en/articles/about-pull-requests#draft-pull-requests). Once the PR is ready for
  review, mark it as ready to convert it to a regular PR.
- After submitting, ensure all GitHub checks pass before requesting a review. Also double-check all static analysis and
  coverage tools show a sufficient coverage and quality level.

#### Triage

- The maintainer in charge of triaging will apply the proper labels for the PR.
- Add the PR to the correct milestone. This should be the same as the issue the PR closes.
- The maintainer can assign a reviewer, or a reviewer can assign themselves.

#### Reviewing

- All reviews will be completed using the GitHub review tool.
- A "Comment" review should be used when there are questions about the code that should be answered, but that don't
  involve code changes. This type of review does not count as approval.
- A "Changes Requested" review indicates that changes to the code need to be made before they will be merged.
- For documentation, special attention will be paid to spelling, grammar, and clarity
  (whereas those things don't matter *as* much for comments in code).
- Reviews are also welcome from others in the community. In the code review, a message can be added, as well as `LGTM`
  if the PR is good to merge. It’s also possible to add comments to specific lines in a file, for giving context to the
  comment.
- PR owner should try to be responsive to comments by answering questions or changing code. If the owner is unsure of
  any comment, please ask for clarification in the PR comments.
- Once all comments have been addressed and all reviewers have approved, the PR is ready to be merged.

#### Merge or Close

- PRs should stay open until they are merged or closed. The issue may be closed if the submitter has not been responsive
  for more than 30 days. This will help keep the PR queue to a manageable size and reduce noise.

## Releases

Hedera uses [Semantic Versioning](https://semver.org) for releases to convey meaning about the underlying code and what
has been modified from one version to the next. We use milestones to track the progress of a release. Assigning issues
to a milestone is on a best effort basis, and we make no guarantees as to when a particular issue will be released. A
milestone (and hence release) is considered done when all outstanding issues and PRs have been closed or moved to
another milestone.