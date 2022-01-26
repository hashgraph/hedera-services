import re
import sys

cron_day_nums = [
    'sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'
]

master_cron_formats = [
    "5 5 * * 6",
    "0 2 * * 3",
    "5 10 * * 6",
    "5 15 * * 6",
    "30 5 * * *",
    "58 3 * * *",
    "45 5 * * *",
    "0 23,8 * * *",
    "15 6 * * *",
    "0 3 * * *",
    "15 3 * * *",
    "30 3 * * *",
    "45 3 * * *",
    "0 4 * * *",
    "30 22 * * *",
    "45 22 * * *",
    "15 22 * * *",
    "15 23 * * *",
    "30 23 * * *",
    "30 6 * * *",
    "40 5 * * *",
    "40 7 * * *",
    "0 5 * * *",
    "55 6 * * *",
    "0 6 * * 2",
    "35 7 * * *",
    "40 4 * * *",
    "30 4 * * *",
    "15 7 * * *",
    "25 7 * * *",
    "25 8 * * *",
    "25 8 * * *",
]

p = r'(.+) (.+) (.+) (.+) (.+)'
def parse_cron_format(f):
    m = re.match(p, f)
    minute, hour, day_of_week  = m.group(1), m.group(2), m.group(5)
    if ',' in hour:
        hour = hour[:hour.index(',')]
    return { 
        'min': int(minute), 
        'hour': int(hour), 
        'wday': day_of_week
    }

master_cron_tabs = [ parse_cron_format(f) for f in master_cron_formats ]

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('USAGE: python3 {} <start-day> <hour-offset-from-master>'.format(sys.argv[0]))
        sys.exit(1)
    day = cron_day_nums.index(sys.argv[1])
    hour_offset = int(sys.argv[2])
    crons_parsed, cron_i = 0, 0
    print('Using (cron) day={}, {} hours offset from master'.format(day, hour_offset))
    with open('.circleci/config.yml', 'r') as fin, open ('config.yml', 'w') as fout:
        for line in fin.readlines():
            if 'cron' in line:
                crons_parsed += 1
                if crons_parsed <= 2:
                    first_cron = False
                    fout.write(line)
                else:
                    master_cron = master_cron_tabs[cron_i]
                    prefix = line[:line.index('"')] + '"'
                    if master_cron['wday'] != '*':
                        master_cron['wday'] = day
                    master_cron['hour'] += hour_offset
                    if master_cron['hour'] < 0:
                        master_cron['hour'] += 24
                    adjusted_line = prefix + '{} {} * * {}'.format(
                        master_cron['min'], master_cron['hour'], master_cron['wday']) + '"\n'
                    fout.write(adjusted_line)
                    cron_i += 1
            else:
                fout.write(line)
