File `all-4byte.directory-method-signatures-2023-02-09.txt.gz` contains all
the contract method selectors with their signatures registered at 
[`https://4byte.directory`](https://4byte.directory) as of 2023-02-09.

These are available from the git repository at 
[`https://github.com/ethereum-lists/4bytes`](https://github.com/ethereum-lists/4bytes),
released under the [MIT license](https://github.com/ethereum-lists/4bytes/blob/master/LICENSE).

Generated from the directory `signatures` in that repo by running

```bash
for file in *; do 
    if [ -f "$file" ]; then
      echo "$file" "$(cat $file)"
    fi
done > ../all-signatures.txt
```

(and since there are more than 910000 files in that directory, each
containing one signature, that script takes a _looong_ time to run<sup>†</sup>.

(This file may or may not contain HAPI method signatures: I haven't
checked.)

----

<sup>†</sup>Almost 4 hours on my MacBook Pro 2021...
