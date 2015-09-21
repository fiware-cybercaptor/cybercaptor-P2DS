#!/bin/sh

#peerhans
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/registration/1?adminKey=default-admin-key
curl -i -v -X POST --header "Content-Type: application/json" -d @./demo/files/peerhans.json http://localhost:12001/p2ds-peer/peer?adminKey=default-admin-key

#hanspeer
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/registration/1?adminKey=default-admin-key
curl -i -v -X POST --header "Content-Type: application/json" -d @./demo/files/hanspeer.json http://localhost:12001/p2ds-peer/peer?adminKey=default-admin-key

#ppeer1
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/registration/1?adminKey=default-admin-key
curl -i -v -X POST --header "Content-Type: application/json" -d @./demo/files/ppeer1.json http://localhost:12001/p2ds-peer/peer?adminKey=default-admin-key


#ppeer2
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/registration/1?adminKey=default-admin-key
curl -i -v -X POST --header "Content-Type: application/json" -d @./demo/files/ppeer2.json http://localhost:12001/p2ds-peer/peer?adminKey=default-admin-key

#ppeer3
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/registration/1?adminKey=default-admin-key
curl -i -v -X POST --header "Content-Type: application/json" -d @./demo/files/ppeer3.json http://localhost:12001/p2ds-peer/peer?adminKey=default-admin-key

#Mark all peers as verified
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/verify/ppeer1?adminKey=default-admin-key\&verified=true
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/verify/ppeer2?adminKey=default-admin-key\&verified=true
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/verify/ppeer3?adminKey=default-admin-key\&verified=true
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/verify/hanspeer?adminKey=default-admin-key\&verified=true
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/verify/peerhans?adminKey=default-admin-key\&verified=true
