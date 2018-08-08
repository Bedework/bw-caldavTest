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
# Runs a subset of tests as a quick check that the server is functional.
#
# ====================================================================
#       Constants
# ====================================================================

# Run this from the quickstart directory

qsDir=`pwd`

testerDir="/Users/mike/IdeaProjects/caldavtester"

outDir="/tmp/cdtester"

# python executable
pythonBin="./venv/bin/python2.7"

# The serverinfo file
serverinfo="$qsDir/bw-caldavTest/src/main/resources/calconnect-tester/testbw/bwserverinfo.xml"

# The output file
outfile="$outDir/cdt.txt"

# The command to run the tester
runTestsOn="$pythonBin testcaldav.py --print-details-onfail -s $serverinfo  -o $outfile"

# This generates a lot of failures - many just wrong status
#	CalDAV/errors.xml \

currentTests=""
currentTests="$currentTests CalDAV/caldavIOP.xml"
currentTests="$currentTests CalDAV/get.xml"
currentTests="$currentTests CalDAV/ical-client.xml"
currentTests="$currentTests CalDAV/propfind.xml"


quicklookTests=""
quicklookTests="$quicklookTests CalDAV/put.xml"
quicklookTests="$quicklookTests CalDAV/reports.xml"
quicklookTests="$quicklookTests CalDAV/sharing-calendars.xml"
quicklookTests="$quicklookTests CalDAV/sharing-create.xml"
quicklookTests="$quicklookTests CalDAV/sharing-feature.xml"
quicklookTests="$quicklookTests CalDAV/sharing-invites.xml"
quicklookTests="$quicklookTests CalDAV/sharing-notification-sync.xml"
quicklookTests="$quicklookTests CalDAV/sharing-sync.xml"

# ====================================================================
# Derived values
# ====================================================================

# ====================================================================
# setup
# ====================================================================

cd $testerDir

mkdir -p $outDir

virtualenv venv
source venv/bin/activate
pip install -r requirements.txt

# ====================================================================
# Get some options
# ====================================================================

usage() {
  printf '\n'
  printf '\n'
  printf '$0 <options1> <options2> \n'
  printf '<options1> is zero or more of: \n'
  printf '\t -sc | --serverconfig <name>\t the server config file\n'
  printf '\t -o | --out <path>\t\t an output file\n'
  printf '\n'
  printf '<options2> is one or more of the following \n'
  printf '           which will be executed immediately: \n'
  printf '\t -ql | --quicklook\t\t run the quicklook tests\n'
  printf '\t -c | --current\t\t\t run the current tests\n'
  printf '\t -a | --all\t\t\t run all tests\n'
  printf '\t -t | --tests <names>\t\t run named tests (must be last)\n'
  printf '\n'
  printf 'e.g \n'
  printf '  $0 -sc myconfig.xml --quicklook -t mytest.xml\n'
  printf 'will run the quicklook then run the test mytest.xml\n'
  printf '\n'
  printf '\n'
}

rm -f $outfile

pwd

while [ "$1" != "" ]
do
  echo "parameter is -->$1<--"
  case $1
  in
    -conf)
      shift
      serverinfo="$1"
      shift
      ;;

    -out)
      shift
      outfile="$1"
      shift
      ;;

    -ql | --quicklook )
      echo "$runTestsOn $quicklookTests"
      $runTestsOn $quicklookTests
      shift
      ;;

    -c | --current )
      $runTestsOn $currentTests
      shift
      ;;

    -a | --all )
      $runTestsOn
      shift
      ;;

    -t | --tests )
      shift
      $runTestsOn "$@"
      exit
      ;;

    -usage | -help | -? | ?)
      usage
      exit
      ;;

    *)
      echo "Unrecognized option $1"
      echo
      usage
      exit 1
      ;;
  esac
done


grep "scripts\.tests\|Suite\|FAILED" $outDir/cdt.txt
grep "Ran " $outDir/cdt.txt
