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

# Ensure cded into directory containing this script

currentDir=`pwd`

testerDir="/Users/mike/IdeaProjects/caldavtester"

outDir="/tmp/cdtester"

cd $testerDir

mkdir -f $outDir

virtualenv venv
source venv/bin/activate
pip install -r requirements.txt

python testcaldav.py $@ --print-details-onfail -s $currentDir/bwserverinfo.xml -o $outDir/cdt.txt \
	CalDAV/caldavIOP.xml \
	CalDAV/errors.xml \
	CalDAV/get.xml \
	CalDAV/ical-client.xml \
	CalDAV/propfind.xml \
	CalDAV/put.xml \
	CalDAV/reports.xml \
	CalDAV/sharing-calendars.xml \
	CalDAV/sharing-create.xml \
	CalDAV/sharing-feature.xml \
	CalDAV/sharing-invites.xml \
	CalDAV/sharing-notification-sync.xml \
	CalDAV/sharing-sync.xml
