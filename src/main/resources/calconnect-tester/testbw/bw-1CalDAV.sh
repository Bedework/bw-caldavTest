#! /bin/bash

#
##
# Copyright (c) 2009-2016 Apple Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
##
#
# Runs a single caldav test.
#

echo "$0"
echo "$1"

# Run this from the quickstart directory

qsDir=`pwd`

testerDir="/Users/mike/IdeaProjects/caldavtester"

outDir="/tmp/cdtester"

cd $testerDir

mkdir -p $outDir

virtualenv venv
source venv/bin/activate
pip install -r requirements.txt

pwd

cmd="./venv/bin/python2.7 testcaldav.py --print-details-onfail -s $qsDir/bw-caldavTest/src/main/resources/calconnect-tester/testbw/bwserverinfo.xml -o $outDir/cdt.txt \
	CalDAV/${1}.xml"

echo $cmd
$cmd
