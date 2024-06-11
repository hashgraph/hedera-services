Platform Design Proposal Process
================================

This document describes the process for creating and reviewing platform design proposals.

The status of all active design proposals can be viewed
at [Platform Design Proposals](https://github.com/orgs/hashgraph/projects/73/views/1) project board.

---

# Creating A Proposal Draft

All proposals must be presented in the form of a PR containing the proposal in a sub-directory
of `platform-sdk/docs/proposals/`.

1. Create an issue in the github repository for the creation of the proposal.
2. Create a new branch for the proposal.
3. Copy the template `temaplate.md` into a subdirectory of `platform-sdk/docs/proposals/` with the name of the
   proposal.
4. Fill out the template with the details of the proposal, removing any irrelevant sections.
    * Images and other files may be included in the proposal directory and linked from the proposal.
5. Submit a draft PR with the proposal documents.
6. Solicit feedback by stakeholders and other design collaborators.
7. When the proposal is ready for a vote, mark the PR as ready for review.

Any source code that was developed as part of the design process may be included in the subdirectory of the proposal
but not merged into the source code of the platform.

While in this draft phase the proposal may be updated and modified as needed. It is best practice to solicit review from
stakeholders to surface any potential change requests while the proposal is in the draft phase. All grammar and spelling
errors should be caught during this phase.

---

# Voting on A Proposal

When a proposal is ready for a vote, the PR is marked as ready for review. The proposal must stay in the Voting state
for 3 business days and cannot be altered during this time.

Votes are cast in comments on the PR on a range between -1 to -0, +0 to +1 with the following semantics:

* -1: is a veto.
* -0 >= x > -1: is a non-blocking vote against the proposal.
* +0 <= x < +1: is a vote in favor of the proposal, with some sense of improvement needed.
* +1: is a vote in favor of the proposal with no changes needed.

The magnitude of the numeric value of the vote indicates the strength of the sentiment behind the vote.

Any veto will permanently block the proposal from being accepted.

Any vote other than +1 should be accompanied by an explanation of the improvement needed or the reason for the vote
against.

---

# Acceptance of A Proposal

After 3 business days of voting, if the proposal has 3 or more votes in favor with no vetos and the proposal has not
been withdrawn or superseded, then the proposal becomes accepted. Once accepted, implementation may begin.

---

# Superseding A Proposal

A proposal may be superseded by a new proposal. The old proposal must link to the new proposal and indicate that it is
being superseded. After the proposal has been updated, the PR's status in the proposal project should be changed
to `Superseded`.

---

# Withdrawing A Proposal

A proposal may be withdrawn. The proposal must be updated with a reason for the withdrawal. After the proposal has been
updated, the PR's status in the proposal project should be changed to `Withdrawn`.

---

# Delivery of A Proposal

Once an accepted proposal has been completely implemented and the code merged into develop, the proposal's content
should be used to update the documentation of the platform. Once the platform documentation is updated, the status of
the proposal PR in the proposal project should be changed to `Delivered`.