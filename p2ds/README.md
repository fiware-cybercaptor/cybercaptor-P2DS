[![License badge](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Documentation badge](https://img.shields.io/badge/docs-latest-green.svg)](http://cybercaptor.readthedocs.org/projects/cybercaptor-p2ds/en/latest/)
[![Docker badge](https://img.shields.io/badge/docker-latest-blue.svg)](https://github.com/fiware-cybercaptor/cybercaptor-P2DS/tree/master/p2ds/docker)
[![Support badge]( https://img.shields.io/badge/support-issues-yellowgreen.svg)](https://github.com/fiware-cybercaptor/cybercaptor-P2DS/issues)

P2DS
====

# Licence

This project contains ZHAW's Contribution towards Privacy-Preserving
Data Sharing (P2DS) for the Cybersecurity GE in FIWARE.

Copyright &copy; 2015 Zürcher Hochschule der Angewandten Wissenschaften
(ZHAW).

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

# Introduction

The main issue is the following: when organisations are asked to share
data about security, they are naturally reluctant to do so, because
revealing this data may lead to loss of trust or it may reveal details
of the organisation's business that a competitor could use to its
advantage.  On the other hand, sharing data could be mutually
beneficial. For example, when an organisation is the victim of a
denial-of-service attack, it is useful to know whether other
organisations are also a victim. This is where privacy-preserving data
sharing comes in.

The technology for P2DS, called [SEPIA](http://www.sepia.ee.ethz.ch/)
was developed as part of a PhD thesis at ETH Zurich. The scenario for
SEPIA is as follows:

![Scenario](docs/scenario_small.png)

Let's say we have three organisations, called Domain 1, Domain 2, and
Domain 3 in the graphic, that want to know the total number of attacks
seen in the last 24 hours, with a granularity of five minutes. In
mathematical terms, what these organisations want is x<sub>1</sub>
+ x<sub>2</sub> + x<sub>3</sub>, where x<sub>1</sub>, x<sub>2</sub>,
and x<sub>3</sub> are vectors with 24*60/5 = 288 elements, and they
want to do this without revealing their own x<sub>i</sub> to any of the
other domains. Here is how the three domains could use P2DS for their
needs.

First, each domain provides an *input peer*. This is a service that is
run by each domain, which knows the original, private x<sub>i</sub> from
domain i.

Next, someone provides a number of *privacy peers*. These services can be
run by anyone; they never have access to unencrypted data, so it doesn't
matter who runs them. The only thing that matters is that the privacy peers
are for the most part *diligent*, i.e., faithfully carry out their assigned
task. The SEPIA protocol can tolerate a small number of malicious peers;
only when more than this number of peers are malicious will the computation
be deemed unsuccessful. The privacy peers execute a multi-round protocol
in which they exchange encrypted information and perform computations on these encrypted values to get yet more encrypted values.  No one learns the cleartext
values of these encrypted vectors, not the privacy peers, not the domains.

But when the computation is finished, the end result becomes available
in the clear and each domain can learn the value of x<sub>1</sub>
+ x<sub>2</sub> + x<sub>3</sub>. For example, Domain 1 learns the value of
x<sub>1</sub> + x<sub>2</sub> + x<sub>3</sub>, but knows nothing about
x<sub>2</sub> or x<sub>3</sub>, except, trivially, their sum.

In our GE, the privacy peers are provided by the domains.

A final component of our contribution is the *group manager*. This is the
service that knows which input peers and which privacy peers should cooperate
in a computation, which keeps the peers' public key certificates, and which
provides SEPIA configuration when it is time to start the computation. It
does not have to be especially trusted (none of the data it has is particularly
secret) but it must be *authentic*, in the sense that the data it keeps should
be protected against unauthorised alteration.

There are a few caveats in using SEPIA:

* When there are only two input peers and they compute a sum, each peer can
  compute the contribution of the other peer through a simple subtraction:
  If I know x and x + y, I can compute y as (x + y) - x.
* In general, when there are n &ge; 3 input peers and n - 1 of them collude to
  defraud the remaining one, they can simply exchange their own input vectors
  through a side channel.
* In general, SEPIA is secure in what is known as the *honest but curious
  adversary* model, in which adversaries try to learn the contents of messages
  but will not actively try to disrupt the protocol.

# Basic Installation

Basic installation is really simple and is covered in the
[Installation and Administration Guide](installation-guide.md). See the section on "Installing from WAR".


You received two war files in the distribution:

* `p2ds-peer.war`, the Input and Privacy Peer service;
* `p2ds-group-management.war`, the group manager.

Each participating organisation must deploy the input and privacy peer
WAR files; only the managing organisation should deploy the group
manager.


# Basic Acceptance Tests

To test whether deployment was successful, try this, adjusting
hostnames and port numbers appropriatey.

```bash
curl -i -v -X POST --header "Content-Type: application/json" -d '{"name":"huhu"}' http://localhost:12001/p2ds-group-management/group-mgmt/group?adminKey=default-admin-key
curl -i -v -X DELETE http://localhost:12001/p2ds-group-management/group-mgmt/group/1?adminKey=default-admin-key
```

This should give you a HTTP return code of 200 twice, and it should
also give you a group ID of "1" in the first response.

# API Walkthrough

The walkthrough here is essentially an annotated version of the tests
in `grp.sh`, which is included in the source distribution.

```bash
#Create group
curl -i -v -X POST --header "Content-Type: application/json" -d '{"name":"huhu"}' http://localhost:12001/p2ds-group-management/group-mgmt/group?adminKey=default-admin-key

#Register hanspeer
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/registration/1?adminKey=default-admin-key
curl -i -v -X POST http://localhost:12001/p2ds-input-peer/peer/register/hanspeer?registrationCode=TEST

#Register peerhans
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/registration/1?adminKey=default-admin-key
curl -i -v -X POST http://localhost:12001/p2ds-input-peer/peer/register/peerhans?registrationCode=TEST

#Register ppeer
curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/registration/1?adminKey=default-admin-key
curl -i -v -X POST http://localhost:12001/p2ds-privacy-peer/peer/register/ppeer?registrationCode=TEST

curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/registration/1?adminKey=default-admin-key
curl -i -v -X POST http://localhost:12001/p2ds-privacy-peer/peer/register/ppeer2?registrationCode=TEST

curl -i -v -X POST http://localhost:12001/p2ds-group-management/group-mgmt/registration/1?adminKey=default-admin-key
curl -i -v -X POST http://localhost:12001/p2ds-privacy-peer/peer/register/ppeer3?registrationCode=TEST

#Check some things
curl -i -v -X GET http://localhost:12001/p2ds-group-management/group-mgmt/groupMembers/1?registrationCode=TEST
curl -i -v -X GET http://localhost:12001/p2ds-group-management/group-mgmt/groupInfo/1?registrationCode=TEST
curl -i -v -X GET http://localhost:12001/p2ds-group-management/group-mgmt/groupMembers/2?registrationCode=TEST
curl -i -v -X GET http://localhost:12001/p2ds-group-management/group-mgmt/groupInfo/2?registrationCode=TEST

#Set GroupConfiguration
curl -i -v -X POST --header "Content-Type: application/json" -d '{"field":"1013","gid":"1","maxElement":"1000","mpcProtocol":"additive","numberOfItems":"2","numberOfTimeSlots":"2"}' http://localhost:12001/p2ds-group-management/group-mgmt/configuration/1?adminKey=default-admin-key

#Start the privacy peer
curl -i -v -X POST http://localhost:12001/p2ds-privacy-peer/peer/start/ppeer/3?registrationCode=TEST
curl -i -v -X POST http://localhost:12001/p2ds-privacy-peer/peer/start/ppeer2/4?registrationCode=TEST
curl -i -v -X POST http://localhost:12001/p2ds-privacy-peer/peer/start/ppeer3/5?registrationCode=TEST

#Start the input peers
curl -i -v -X POST http://localhost:12001/p2ds-input-peer/peer/start/hanspeer/1?registrationCode=TEST
curl -i -v -X POST http://localhost:12001/p2ds-input-peer/peer/start/peerhans/2?registrationCode=TEST

#Add input data
curl -i -v -X POST --header "Content-Type: application/json" -d '{"peerName":"hanspeer","data":"1;3"}' http://localhost:12001/p2ds-input-peer/peer/input?registrationCode=TEST
curl -i -v -X POST --header "Content-Type: application/json" -d '{"peerName":"hanspeer","data":"4;5"}' http://localhost:12001/p2ds-input-peer/peer/input?registrationCode=TEST

#curl -i -v -X POST http://localhost:12001/p2ds-input-peer/peer/stop/peerhans?registrationCode=TEST

curl -i -v -X POST --header "Content-Type: application/json" -d '{"peerName":"peerhans","data":"2;5"}' http://localhost:12001/p2ds-input-peer/peer/input?registrationCode=TEST
curl -i -v -X POST --header "Content-Type: application/json" -d '{"peerName":"peerhans","data":"3;4"}' http://localhost:12001/p2ds-input-peer/peer/input?registrationCode=TEST
```

# Additional Documentation

* [Installation and Administration Guide](docs/installation-guide.md)
* [User and Programmer's Guide](docs/user-guide.md)
