# Copyright 2015 Zürcher Hochschule der Angewandten Wissenschaften
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#!/bin/sh

#Set group-mgmt service to test mode
#Don't do that in production!
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/testmode?on=true\&adminKey=default-admin-key

#Create group
curl -i -v -X POST --header "Content-Type: application/json" -d '{"name":"SimpleGroup"}' http://localhost:12001/p2ds-group-management/group-mgmt/group?adminKey=default-admin-key

#Configure peers
./configPeers.sh

#Set GroupConfiguration
curl -i -v -X POST --header "Content-Type: application/json" -d '{"field":"1013","gid":"1","maxElement":"1000","mpcProtocol":"additive","numberOfItems":"2","numberOfTimeSlots":"2"}' http://localhost:12001/p2ds-group-management/group-mgmt/configuration?adminKey=default-admin-key

#Start the privacy peer
curl -i -v -X POST http://localhost:12001/p2ds-peer/start/ppeer1?registrationCode=TEST
curl -i -v -X POST http://localhost:12001/p2ds-peer/start/ppeer2?registrationCode=TEST
curl -i -v -X POST http://localhost:12001/p2ds-peer/start/ppeer3?registrationCode=TEST

#Start the input peers
curl -i -v -X POST http://localhost:12001/p2ds-peer/start/hanspeer?registrationCode=TEST
curl -i -v -X POST http://localhost:12001/p2ds-peer/start/peerhans?registrationCode=TEST

#Add input data
curl -i -v -X POST --header "Content-Type: application/json" -d '{"peerName":"hanspeer","data":["1;3","4;5"]}' http://localhost:12001/p2ds-peer/inputs?registrationCode=TEST

curl -i -v -X POST --header "Content-Type: application/json" -d '{"peerName":"peerhans","data":"2;5"}' http://localhost:12001/p2ds-peer/input?registrationCode=TEST
curl -i -v -X POST --header "Content-Type: application/json" -d '{"peerName":"peerhans","data":"3;4"}' http://localhost:12001/p2ds-peer/input?registrationCode=TEST


