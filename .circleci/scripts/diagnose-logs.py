import os
import re
import sys
import json
from collections import namedtuple
from collections import defaultdict

LOGS_PATH=''
RESOURCES_PATH=''
DIAGNOSTICS_PATH=''

def safe_argv(i):
    return sys.argv[i] if len(sys.argv) > i else None

def orElse(u, v):
    return u if u else v

def slack_msg_rec_file():
    return '{}/slack_msg.txt'.format(DIAGNOSTICS_PATH)

def filter_patterns_file():
    return '{}/filter-patterns.json'.format(RESOURCES_PATH)

def upload_diagnostics_fingerprint_file():
    return '{}/shouldUploadFilteredLogs'.format(DIAGNOSTICS_PATH)

def recommend_slack_msg(lines):
    with open(slack_msg_rec_file()) as f:
        for line in lines:
            f.write('{}\n'.format(line))

def echo_env():
    print('Will diagnose logs from {}'.format(LOGS_PATH))
    print(' -> Using patterns from {}'.format(filter_patterns_file()))
    print(' -> Writing Slack msg recommendation to {}'.format(slack_msg_rec_file()))

FILTER_PATTERNS=None
def load_resources():
    global FILTER_PATTERNS
    with open(filter_patterns_file()) as f:
        FILTER_PATTERNS = json.load(f)

Node = namedtuple('Node', ['public_ip', 'account'])
Diagnosis = namedtuple('Diagnosis', ['implied_exit_code', 'should_retry', 'reason'])
DEFAULT_REASON='no recurring infrastructure issue was detected'
DEFAULT_DIAGNOSIS = Diagnosis(1, False, DEFAULT_REASON)

def net_of(multiple):
    exit_code_so_far = 1
    retry_is_possible = False
    reasoning = DEFAULT_REASON
    for diag in multiple:
        suspicious = diag.implied_exit_code > 0
        if suspicious:
            if diag.implied_exit_code > exit_code_so_far:
                reasoning = diag.reason
                retry_is_possible = diag.should_retry
            else:
                reasoning = diag.reason if reasoning is DEFAULT_REASON else reasoning
            exit_code_so_far = max(diag.implied_exit_code, exit_code_so_far)
            retry_is_possible = retry_is_possible or diag.should_retry
    return Diagnosis(exit_code_so_far, retry_is_possible, reasoning)

Details = namedtuple('Details', ['pattern', 'rc', 'filter_after', 'should_retry', 'text'])

def as_pattern_tuple(o):
    return Details(
            o['pattern'], 
            o['impliedExitCode'], 
            o.get('filterAfterOccurrences', -1),
            o.get('shouldRetry', False),
            o.get('readableInference', ''))

def merged(a, b):
    return net_of((a, b))

def process(log, path, patterns):
    if not os.path.exists(path):
        print('WARN: No such log "{}" skipping it!'.format(path))
        return DEFAULT_DIAGNOSIS

    final_diagnosis = DEFAULT_DIAGNOSIS
    id_counts = defaultdict(lambda: 0) 
    filter_counts = defaultdict(lambda: 0) 
    filter_examples = {}
    id_details = dict([p['id'], as_pattern_tuple(p)] for p in patterns)
    filtered_lines = []

    with open(path) as f:
        for line in f.readlines():
            matches = [(pid, details) 
                            for (pid, details) in id_details.items()
                            if line.find(details.pattern) > -1]
            if matches:
                # Pick the most pattern with the largest return code
                matches.sort(key=lambda x: x[1].rc, reverse=True)
                priority_match = matches[0]
                pid, details = priority_match[0], priority_match[1]
                id_counts[pid] += 1
                # Only merge the first occurrence of problematic diagnoses
                if id_counts[pid] == 1 and details.rc > 0:
                    initial_diagnosis = Diagnosis(details.rc, details.should_retry, details.text)
                    final_diagnosis = merged(final_diagnosis, initial_diagnosis)
                if id_counts[pid] <= details.filter_after:
                    filtered_lines.append(line)
                else:
                    filter_counts[pid] += 1
                    if filter_counts[pid] == 1:
                        filter_examples[pid] = line
            else:
                filtered_lines.append(line)
        if filter_examples:
            filtered_lines.append(''.join('.' for _ in range(72)) + '\n')
            for pid in filter_examples:
                filtered_lines.append('And {} more lines similar to \n\t{}'.format(
                    filter_counts[pid], filter_examples[pid]))

    filtered_path = path[0:len(path) - 4] + '-filtered.log'
    print(' -> Writing {} filtered lines to {}'.format(len(filtered_lines), filtered_path))
    with open(filtered_path, 'w') as f:
        f.writelines(filtered_lines)

    return final_diagnosis 

def diagnose(node):
    output_dir = os.path.join(LOGS_PATH, node.public_ip, 'output')
    diagnoses = []
    print('\n********* {} ********'.format(node.public_ip))
    for log in ['hgcaa', 'swirlds']:
        log_file = os.path.join(output_dir, '{}.log'.format(log))
        patterns = FILTER_PATTERNS[log]
        print('Now diagnosing {}'.format(log_file)) 
        diagnosis = process(log, log_file, patterns)
        print(' -> {}'.format(diagnosis))
        diagnoses.append(diagnosis)
    return net_of(diagnoses)

def if_avail(var_name):
    return os.environ.get(var_name, '<n/a>')

def account_for(host_dir):
    ACCOUNT_PATTERN = re.compile(r'account\d+[.]\d+[.]\d+')
    contents = os.scandir(host_dir)
    account_fingerprint = [i.name for i in contents if re.match(ACCOUNT_PATTERN, i.name)][0]
    account = account_fingerprint[len('account'):]
    return account
        
if __name__ == '__main__':
    LOGS_PATH = orElse(safe_argv(1), '.')
    DIAGNOSTICS_PATH = orElse(safe_argv(2), '.')
    RESOURCES_PATH = orElse(safe_argv(3), '.')
    echo_env() 

    load_resources()

    IP_PATTERN = re.compile(r'\d+[.]\d+[.]\d+[.]\d+') 
    nodes = [Node(d.name, account_for(os.path.join(LOGS_PATH, d.name)))
                for d in os.scandir(LOGS_PATH) 
                if re.match(IP_PATTERN, d.name)]
    print(' -> For discovered nodes {}'.format(nodes))
    diagnoses = [diagnose(node) for node in nodes]
    diagnosis = net_of(diagnoses)
    print('\nFINAL DIAGNOSIS :: {}'.format(diagnosis))

    branch = if_avail('CIRCLE_BRANCH')
    build_no = if_avail('CIRCLE_BUILD_NUM')
    build_url = if_avail('CIRCLE_BUILD_URL')
    github_user = if_avail('CIRCLE_USERNAME')
    if diagnosis.implied_exit_code:
        with open(slack_msg_rec_file(), 'w') as f:
            msg = 'job #{} of `{}` failed'.format(build_no, branch)
            if diagnosis.should_retry:
                msg += ', but probably just because {}. '.format(diagnosis.reason) \
                        + 'You may want to re-run the workflow: {}'.format(build_url)
            else:
                msg += ', and {}. There is likely '.format(diagnosis.reason) \
                        + 'no reason to re-run the workflow: {}'.format(build_url)
            f.writelines(['{}\n'.format(msg)])
        if not diagnosis.should_retry:
            with open(upload_diagnostics_fingerprint_file(), 'w') as f:
                f.writelines(['YES\n'])
