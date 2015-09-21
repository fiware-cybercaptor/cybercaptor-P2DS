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



~/web/web2/bin/shutdown.sh

rm /var/p2ds/db_p2ds.db
rm -rf ~/web/web2/webapps/p2ds-group-management
rm -rf ~/web/web2/webapps/p2ds-peer
rm -rf ~/web/web2/webapps/p2ds-receiver
rm ~/web/web2/webapps/p2ds-group-management.war
rm ~/web/web2/webapps/p2ds-peer.war
rm ~/web/web2/webapps/p2ds-receiver.war
cp -f ./group-management/target/p2ds-group-management.war ~/web/web2/webapps/p2ds-group-management.war
cp -f ./peer/target/p2ds-peer.war ~/web/web2/webapps/p2ds-peer.war
cp -f ./receiver/target/p2ds-receiver.war ~/web/web2/webapps/p2ds-receiver.war


~/web/web2/bin/startup.sh
echo > ~/web/web2/logs/catalina.out

#sudo service tomcat7 restart
