import os
import sys
import json
import slack
import argparse
import ntpath

GITHUB_TO_SLACK_USER_ID = {
    'tinker-michaelj': 'UKW68U6TD',
    'qnswirlds': 'UEVPV4HDY',
    'JeffreyDallas': 'UB15L2FLJ',
    'QianSwirlds': 'UE4P47SMT',
    'anighanta': 'ULV8PHZ9N',
    'nathanklick': 'UA66NE2NT',
    'mike-burrage-hedera': 'UJLNNSUPR',
    'ljianghedera': 'UMQ7SUGBE',
}

CHANNEL_NAME_TO_CHANNEL_ID = {
    'hedera-cicd': 'CMD3V6ZC4',
    'hedera-regression': 'CKWHL8R9A',
}

def get_slack_channel_id(args):
    if args.channel:
        return CHANNEL_NAME_TO_CHANNEL_ID.get(args.channel)

def get_github_user(args):
    if args.github_user:
        return args.github_user
    if get_env_var('CIRCLECI') == 'true':
        print('Get GitHub user from CircleCI env var')
        return get_env_var('CIRCLE_USERNAME')

def get_slack_user_id(args):
    if args.slack_user_id:
        return args.slack_user_id

    github_user = get_github_user(args)
    return GITHUB_TO_SLACK_USER_ID.get(github_user)

def get_file_name(args):
    if args.file_name:
        return args.file_name
    if args.file:
        return ntpath.basename(args.file)

def get_env_var(env_var):
    return os.environ.get(env_var)

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
                        default='',
                        dest='file_name')
    parser.add_argument('-u', '--user',
                        help='Slack user to @mention in the message',
                        default='',
                        dest='slack_user_id')
    parser.add_argument('-g', '--github-user',
                        help='GitHub user to @mention in the message (requires GITHUB_TO_SLACK_USER_ID entry)',
                        default='',
                        dest='github_user')
    parser.add_argument('-c', '--channel',
                        help='Slack channel name',
                        dest='channel')
    parser.add_argument('-a', '--at-here',
                        help='@mention the whole channel',
                        action='store_true',
                        dest="at_here")

    args = parser.parse_args(sys.argv[1:])
    if (not bool(args.text or args.file)):
        print('Neither --text nor --file was given to {}, exiting now!'.format(sys.argv[0]))
        sys.exit()

    slack_channel_id = get_slack_channel_id(args)
    print("slack_channel_id: {}".format(slack_channel_id))

    slack_user_id = get_slack_user_id(args)
    print("slack_user_id: {}".format(slack_user_id))

    if slack_channel_id == None:
        if slack_user_id == None:
            print('Slack channel and/or user must be provided')
            sys.exit()
        else:
            slack_channel_id = slack_user_id

    slack_token = get_env_var('SLACK_API_TOKEN')
    client = slack.WebClient(token=slack_token)
    response = None
    if args.file:
        print('Sending contents of "{}" to {}'.format(args.file, slack_channel_id))
        file_name = get_file_name(args)
        response = client.files_upload(
            channels=slack_channel_id,
            file=args.file,
            filename=file_name,
            title=args.file_name)
    elif args.text:
        literal = ''
        with open(args.text) as f:
            literal = '\n'.join([ line.strip() for line in f.readlines() ])

        if slack_user_id:
            literal = '<@{}> - {}'.format(slack_user_id, literal)

        if args.at_here:
            literal = '<!here> - ' + literal

        print('Sending "{}" to {}'.format(literal, slack_channel_id))
        response = client.chat_postMessage(channel=slack_channel_id, text=literal)

    if response.data:
        print(json.dumps(response.data, indent=4, sort_keys=True))
