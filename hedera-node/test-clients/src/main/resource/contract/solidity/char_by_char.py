ref, rec = '', ''
with open('reference.hex', 'r') as ref_in:
    ref = ref_in.read().strip()
with open('recreation.hex', 'r') as rec_in:
    rec = rec_in.read().strip()

print('REFERENCE ({}) : {}'.format(len(ref), ref))
print('')
print('RECREATION ({}): {}'.format(len(rec), rec))

l = len(ref)
for i in range(l):
    if ref[i] != rec[i]:
        print('Differ at index {} ({} vs {})'.format(i, ref[i], rec[i]))
