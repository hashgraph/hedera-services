import os
import re
import sys
import shutil
import filecmp
from functools import reduce

fcm_types = ['accounts', 'storage', 'topics']

avail_nodes = []
dumps_root_dir = ''
investigation_name = 'iss'
rounds_avail = {}
account_fcm_pattern = re.compile(r'accounts-round(\d+)[.]fcm')
first_round_post_iss = 0

def prepare_env():
    global avail_nodes, dumps_root_dir, investigation_name
    if len(sys.argv) < 3:
        print('USAGE: python3 {} '.format(sys.argv[0]) +
                '<dumps-root-dir> [<investigation-name>]')
        sys.exit(1)
    dumps_root_dir = sys.argv[1]
    investigation_name = sys.argv[2] or investigation_name
    avail_nodes = [n for n in next(os.walk(dumps_root_dir))[1]]
    if not os.path.exists(os.path.join('.', investigation_name)):
        os.mkdir(investigation_name)
    for node in avail_nodes:
        if not os.path.exists(
                os.path.join('.', investigation_name, node)):
            os.mkdir(os.path.join(investigation_name, node))

def load_rounds_avail():
    for node in avail_nodes:
        rounds_dir = os.path.join(dumps_root_dir, node)
        rounds = set([num_from(fcm) for fcm in next(os.walk(rounds_dir))[2] 
                if re.match(account_fcm_pattern, fcm)])
        rounds_avail[node] = rounds

def pick_first_round():
    global first_round_post_iss, rounds_avail
    reducer = lambda x, y: x.intersection(y)
    first_round_post_iss = min(reduce(reducer, rounds_avail.values()))

def num_from(accounts_fcm):
    m = re.match(account_fcm_pattern, accounts_fcm)
    return int(m.group(1))

def copy_round_fcms():
    for node in avail_nodes:
        for fcm_type in fcm_types: 
            f = fcm_file(fcm_type)
            shutil.copyfile(
                    os.path.join(dumps_root_dir, node, f),
                    os.path.join('.', investigation_name, node, f))

def diff_matrix(fcm_type, f):
    field_width = max(map(len, avail_nodes)) + 1
    write_and_print('\n' + ''.join('-' for _ in range(len(fcm_type))), f)
    write_and_print(fcm_type.upper(), f)
    write_and_print(''.join('-' for _ in range(len(fcm_type))), f)
    write_and_print('{:{w}}'.format('', w=field_width) + 
        ''.join(['{:{w}}'.format(node, w=field_width) 
                for node in avail_nodes]), f)
    blank = '{:{w}}'.format('', w=field_width)
    for i, node in enumerate(avail_nodes):
        l = ['{:<{w}}'.format(node, w=field_width)]
        for j, other in enumerate(avail_nodes):
            if j < i:
                l.append(blank)
            else:
                answer = 'X' if differ(node, other, fcm_type) else '.'
                l.append('{:{w}}'.format(answer, w=field_width))
        line = ''.join(l)
        write_and_print(line, f)

def write_and_print(s, f):
    print(s)
    f.write(s + '\n')

def differ(node1, node2, fcm_type):
    fcm1, fcm2 = fcm_path(node1, fcm_type), fcm_path(node2, fcm_type)
    return not filecmp.cmp(fcm1, fcm2)

def fcm_file(fcm_type):
    return '{}-round{}.fcm'.format(fcm_type, first_round_post_iss)

def fcm_path(node, fcm_type):
    return os.path.join('.', investigation_name, node, fcm_file(fcm_type))

def write_list_literals():
    p = os.path.join('.', investigation_name, 'fcm-paths.excerpt')
    with open(p, 'w') as f:
        for fcm_type in fcm_types:
            f.write('   final List<String> {}Locs = List.of(\n'.format(
                fcm_type))
            for i, node in enumerate(avail_nodes):
                fq = os.path.join(
                        os.path.abspath('.'), 
                        investigation_name, node, fcm_file(fcm_type))
                opt_comma = '' if (i == len(avail_nodes) - 1) else ','
                f.write('       "{}"{}\n'.format(fq, opt_comma))
            f.write('   );\n')

if __name__ == '__main__':
    prepare_env()
    load_rounds_avail()
    pick_first_round()
    print('\nRound {} is first available for all nodes.'.format(
        first_round_post_iss) + ' The dumped FCMs differ as below.')
    copy_round_fcms()
    p = os.path.join('.', investigation_name, 'fcm-diffs.txt')
    with open(p, 'w') as f:
        for fcm_type in fcm_types:
            diff_matrix(fcm_type, f)
    write_list_literals()
