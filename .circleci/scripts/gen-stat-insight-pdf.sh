#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh
. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

set +e
cd $STATS_PARENT_DIR
pip3 -q install matplotlib
cp "${REPO}/test-clients/scripts/insight.py" .
ci_echo "Now running 'python3 insight.py -d . -c $STATS_CSV_PREFIX -g -p'..."
python3 insight.py -d . -c $STATS_CSV_PREFIX -g -p
ci_echo "...done running insight.py!"
mv multipage_pdf.pdf $INSIGHT_PY_PDF_PATH
