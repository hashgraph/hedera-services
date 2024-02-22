
def naiveify(dt):
    """
    Takes a datetime object and strips its timezone (works best if it is a UTC datetime, but doesn't check that)
    :param dt: a datetime with a timezone (preferably UTC)
    :return: same datetime but "naive" (without timezone)
    """
    return dt.replace(tzinfo=None)


def set_to_csv(s):
    return ', '.join( [str(e) for e in sorted(s)])


def split_list(batch_size, a):
    """Split a list into batches of the given size (last batch may be short)"""
    for i in range(0, len(a), batch_size):
        yield a[i:i+batch_size]
