#!/bin/sh

curl -i -v -X POST --header "Content-Type: application/json" -d '{"peerName":"hanspeer","data":"2;2"}' http://localhost:12001/p2ds-peer/input?registrationCode=TEST
curl -i -v -X POST --header "Content-Type: application/json" -d '{"peerName":"hanspeer","data":"6;1"}' http://localhost:12001/p2ds-peer/input?registrationCode=TEST
curl -i -v -X POST --header "Content-Type: application/json" -d '{"peerName":"hanspeer","data":"1;5"}' http://localhost:12001/p2ds-peer/input?registrationCode=TEST

curl -i -v -X POST --header "Content-Type: application/json" -d '{"peerName":"peerhans","data":"8;8"}' http://localhost:12001/p2ds-peer/input?registrationCode=TEST
curl -i -v -X POST --header "Content-Type: application/json" -d '{"peerName":"peerhans","data":"5;3"}' http://localhost:12001/p2ds-peer/input?registrationCode=TEST
curl -i -v -X POST --header "Content-Type: application/json" -d '{"peerName":"peerhans","data":"4;7"}' http://localhost:12001/p2ds-peer/input?registrationCode=TEST
