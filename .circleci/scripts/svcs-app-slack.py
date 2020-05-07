import os
import sys
import json
import slack
import argparse
import subprocess
from contextlib import contextmanager

GITHUB_TO_SLACK_USER = {
    'michael.tinker@hedera.com': 'UKW68U6TD',
    'quannguyen@swirlds.com': 'UEVPV4HDY',
    # These strange git users are returned when doing merge
    '45947300+qnswirlds@users.noreply.github.com': 'UEVPV4HDY',
    'anirudh.ghanta@hedera.com': 'ULV8PHZ9N',
    '53790698+anighanta@users.noreply.github.com': 'ULV8PHZ9N',
    'jeffrey@swirlds.com': 'UB15L2FLJ',
    '39912573+JeffreyDallas@users.noreply.github.com': 'UB15L2FLJ',
    'leo.jiang@hedera.com': 'UMQ7SUGBE',
    '55505519+ljianghedera@users.noreply.github.com': 'UMQ7SUGBE',
    'TopCat2': 'UE8DSP2F5',
    'QianSwirlds': 'UE4P47SMT',
    'nathanklick': 'UA66NE2NT',
    'mike-burrage-hedera': 'UJLNNSUPR',
}

CIRCLE_USERNAME_TO_SLACK_USER = {
    'tinker-michaelj': 'UKW68U6TD',
    'qnswirlds': 'UEVPV4HDY',
    'TopCat2': 'UE8DSP2F5',
    'JeffreyDallas': 'UB15L2FLJ',
    'QianSwirlds': 'UE4P47SMT',
    'anighanta': 'ULV8PHZ9N',
    'nathanklick': 'UA66NE2NT',
    'mike-burrage-hedera': 'UJLNNSUPR',
    'ljianghedera': 'UMQ7SUGBE',
}

GITHUB_COMMIT_AUTHOR = 'COMMIT_AUTHOR'


@contextmanager
def cd(newdir):
    prevdir = os.getcwd()
    os.chdir(os.path.expanduser(newdir))
    try:
        yield
    finally:
        os.chdir(prevdir)


def get_github_user(sha1):
    print("What env: {}".format(os.environ.get("CIRCLECI")))
    if os.environ.get("CIRCLECI") == 'true':
        os.system('echo  "\nHost github.com\n\tStrictHostKeyChecking no\n" >> ~/.ssh/config')
        circleci_dir = '/repo/.circleci'
    else:
        circleci_dir = '~/projects/services-hedera/.circleci'

    with cd(circleci_dir):
        print('Current dir: {}'.format(os.getcwd()))
        if sha1:
            result = subprocess.run(["git", "show", "-s", "--format='%ae'", sha1],
                                stdout=subprocess.PIPE)
            github_user = result.stdout.decode('ascii').strip('\'').strip('\'\n')

        return github_user


def get_slack_channel(args):
    if os.environ.get('CIRCLE_BRANCH') == 'dev-mock' and not os.environ.get("CIRCLE_USERNAME"):
        return 'CKWHL8R9A'
    else:
        sha1 = os.environ.get('CIRCLE_SHA1')
        if not sha1 and args.commit_sha1:
            sha1 = args.commit_sha1

        github_user = get_github_user(sha1)
        if not github_user and args.github_user:
            github_user = args.github_user

        channel = GITHUB_TO_SLACK_USER.get(github_user)

    if channel:
        return channel

    if args.channel:
        return args.channel
    return 'DT7EVFDA6'

def get_slack_user(args):
    ci_user = os.environ.get('CIRCLE_USERNAME')
    if not ci_user  and args.ci_user:
        ci_user = args.ci_user
    slack_user = CIRCLE_USERNAME_TO_SLACK_USER.get(ci_user)
    if slack_user:
        return slack_user
    return args.slack_user

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('-t', '--text',
            help='Path to literal text to send to Slack',
            dest='text')
    parser.add_argument('-f', '--file',
            help='Path to file to upload to Slack',
            dest='file')
    parser.add_argument('-n', '--name',
            help='Name of file to upload to Slack',
            default='file',
            dest='name')
    parser.add_argument('-u', '--user',
            help='Slack user to @mention in the message',
            dest='slack_user')
    parser.add_argument('-g', '--github-user',
            help='GitHub user to @mention in the message (requires GITHUB_TO_SLACK_USER entry)',
            default='',
            dest='github_user')
    parser.add_argument('-c', '--channel',
            help='Slack ID of target channel',
            dest='channel')
    parser.add_argument('-s', '--commit_sha1',
            help='Commit hash triggering this build',
            default='bad-commit',
            dest='commit_sha1')
    parser.add_argument('-C', '--circle_username',
            help='The CIRCLE_USERNAME that triggers this build',
            dest='ci_user')


    args = parser.parse_args(sys.argv[1:])
    if (not bool(args.text or args.file)):
        print('Neither --text nor --file was given to {}, exiting now!'.format(sys.argv[0]))
        sys.exit()

    slack_channel = get_slack_channel(args)

    print("slack channel : {}".format(slack_channel))

    if slack_channel == 'CKWHL8R9A' and not os.environ.get('CIRCLE_USERNAME'):
        slack_user = slack_channel
    else:
        slack_user = get_slack_user(args)

    print("slack_user : {}".format(slack_user))

    slack_token = os.environ['SLACK_API_TOKEN']
    client = slack.WebClient(token=slack_token)
    response = None
    if args.file:
        print('Sending contents of "{}" to {}'.format(args.file, slack_channel))
        response = client.files_upload(
                channel=slack_channel,
                file=args.file,
                filename=args.name,
                title=args.name)
    elif args.text:
        literal = ''
        with open(args.text) as f:
            literal = ' / '.join([ line.strip() for line in f.readlines() ])

        if slack_user:
            literal = '<@{}> - {}'.format(slack_user, literal)

        print('Sending "{}" to {}'.format(literal, slack_channel))
        response = client.chat_postMessage(channel=slack_channel, text=literal)

    if response.data:
        print(json.dumps(response.data, indent=4, sort_keys=True))
