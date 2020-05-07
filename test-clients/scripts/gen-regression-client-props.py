import cmd
import sys

TXN_PROVIDERS = {
    'SystemDelete': None,
    'SystemUndelete': None,
    'ContractCall': 'randomCall',
    'ContractCreate': 'randomContract',
    'ContractDelete': 'randomContractDeletion',
    'ContractUpdate': None,
    'CryptoCreate': 'randomAccount',
    'CryptoDelete': 'randomAccountDeletion',
    'CryptoTransfer': 'randomTransfer',
    'CryptoUpdate': 'randomAccountUpdate',
    'FileAppend': 'randomAppend',
    'FileCreate': 'randomFile',
    'FileDelete': 'randomFileDeletion',
    'FileUpdate': 'randomFileUpdate',
    'ConsensusCreateTopic': None,
    'ConsensusUpdateTopic': None,
    'ConsensusDeleteTopic': None,
    'ConsensusSubmitMessage': None
}
QUERY_PROVIDERS = {
        'ContractCallLocal': 'randomCallLocal',
        'CryptoGetInfo': 'randomAccountInfo',
        'CryptoGetAccountRecords': 'randomAccountRecords',
        'FileGetInfo': 'randomFileInfo',
        'FileGetContents': 'randomContents',
        'TransactionGetRecord': 'randomRecord',
        'TransactionGetReceipt': 'randomReceipt'
}
OP_PROVIDERS = dict(list(TXN_PROVIDERS.items()) + list(QUERY_PROVIDERS.items()))
SUPPORTED_TXNS = [k for (k, v) in TXN_PROVIDERS.items() if v]
SUPPORTED_QUERIES = [k for (k, v) in QUERY_PROVIDERS.items() if v]
SUPPORTED_OPS = SUPPORTED_TXNS + SUPPORTED_QUERIES
MAX_NAME_LEN = max(map(len, SUPPORTED_OPS))

class ClientProfileShell(cmd.Cmd):
    intro = 'Interactive construction of client profile for UmbrellaRedux. Type help or ? to list commands.'
    prompt = '(client) '

    targets = {}
    other_props = {}
    max_pending_ops = None
    backoff_sleep_secs = None
    status_timeout_secs = None

    def do_otherprop(self, args):
        try:
            name, value = args.split()
            self.other_props[name] = value
        except:
            print("Oops! USAGE: otherprop <name> <value>")

    def do_maxpendn(self, args):
        'Set the max pending ops for the ProviderRun config'
        try:
            self.max_pending_ops = int(args)
        except:
            print("Oops! USAGE: maxpend <n>")

    def do_backoff(self, args):
        'Set the number of seconds to backoff if pendings ops exceeds allowed max'
        try:
            self.backoff_sleep_secs = int(args)
        except:
            print("Oops! USAGE: backoff <n>")

    def do_timeout(self, args):
        'Set the number of seconds to wait for receipt other than UNKNOWN'
        try:
            self.status_timeout_secs = int(args)
        except:
            print("Oops! USAGE: timeout <n>")

    def do_targetops(self, args):
        'Set the target per-sec executions for a HAPI operation: targetops CryptoTransfer 2500'
        try:
            op, target = args.split()
            self.targets[op] = float(target)
            print('Set target for {} to {} ops'.format(op, target))
        except:
            print("Oops! USAGE: targetops <op> <per-sec>")
    def complete_targetops(self, text, line, begin, end):
        if not text:
            return SUPPORTED_OPS
        else:
            return list(filter(lambda op: op.startswith(text), SUPPORTED_OPS))

    def do_genprops(self, args):
        'Generate the configured regression-<profile>.properties for UmbrellaRedux'
        try:
            profile = args
            no_ops = [op for op in SUPPORTED_OPS if op not in self.targets.keys()]
            max_total_ops = sum(self.targets.values())
            min_tgt = min([v for (_, v) in self.targets.items()])
            biases = dict((k, int(v / min_tgt)) for (k, v) in self.targets.items())
            with open('src/main/resource/eet-config/regression-{}.properties'.format(profile), 'w') as f:
                f.write('#### AUTOMATION ####\n')
                optional_args = ','.join(
                        '{}={}'.format(k, v) for (k, v) in
                            [('maxPendingOps', self.max_pending_ops),
                                ('backoffSleepSecs', self.backoff_sleep_secs),
                                ('statusTimeoutSecs', self.status_timeout_secs)] if v)
                if optional_args:
                    optional_args = ',{}'.format(optional_args)
                f.write('# maxOpsPerSec={}{}\n'.format(
                    int(max_total_ops), optional_args))
                f.write('####____________####\n\n')
                f.write('#### ACTIVE OPS ####\n')
                for (k, v) in biases.items():
                    f.write('{}.bias={}\n'.format(OP_PROVIDERS[k], v))
                if self.other_props:
                    f.write('#### CONFIGURED ####\n')
                    for name, value in self.other_props.items():
                        f.write('{}={}\n'.format(name, value))
                f.write('####____________####\n\n')
                f.write('#### IGNORE OPS ####\n')
                for k in no_ops:
                    f.write('{}.bias=0\n'.format(OP_PROVIDERS[k]))
                f.write('####____________####\n\n')
        except Exception as e:
            print(str(e))
            print("Oops! USAGE: genprops <profile>")
    def do_summary(self, args):
        'Summarize the target per-sec executions for all HAPI operations mentioned so far'
        for k, v in self.targets.items():
            print('{:<{width}} :: {}'.format(k, v, width=MAX_NAME_LEN))
        if self.other_props:
            print('...')
            for name, value in self.other_props.items():
                print('{}={}'.format(name, value))

if __name__ == '__main__':
    ClientProfileShell().cmdloop()
