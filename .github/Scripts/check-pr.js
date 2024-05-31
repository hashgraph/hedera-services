const axios = require('axios');

const githubToken = process.env.GITHUB_TOKEN;
const { GITHUB_REPOSITORY, GITHUB_PR_NUMBER } = process.env;

const [owner, repo] = GITHUB_REPOSITORY.split('/');

async function getPRDetails() {
    const url = `https://api.github.com/repos/${owner}/${repo}/pulls/${GITHUB_PR_NUMBER}`;
    const response = await axios.get(url, {
        headers: {
            Authorization: `token ${githubToken}`
        }
    });
    return response.data;
}

async function getIssueDetails(issueNumber) {
    const url = `https://api.github.com/repos/${owner}/${repo}/issues/${issueNumber}`;
    const response = await axios.get(url, {
        headers: {
            Authorization: `token ${githubToken}`
        }
    });
    return response.data;
}

async function run() {
    try {
        const pr = await getPRDetails();
        const { milestone: prMilestone, body: prBody, assignees: prAssignees } = pr;

        if (prAssignees.length === 0) {
            throw new Error('The PR has no assignees.');
        }
        if (!prMilestone) {
            throw new Error('The PR has no milestone.');
        }

        const issueNumberMatches = prBody.match(/#(\d+)/g);

        if (!issueNumberMatches) {
            console.log('No associated issues found in PR description.');
        } else {
            for (const match of issueNumberMatches) {
                const issueNumber = match.replace('#', '');
                const issue = await getIssueDetails(issueNumber);
                const { assignees: issueAssignees, milestone: issueMilestone } = issue;

                if (issueAssignees.length === 0) {
                    throw new Error(`Associated issue #${issueNumber} has no assignees.`);
                }
                if (!issueMilestone) {
                    throw new Error(`Associated issue #${issueNumber} has no milestone.`);
                }
            }
        }

        console.log('PR and all associated issues have assignees and milestones.');
    } catch (error) {
        console.error(error.message);
        process.exit(1);
    }
}

run();
