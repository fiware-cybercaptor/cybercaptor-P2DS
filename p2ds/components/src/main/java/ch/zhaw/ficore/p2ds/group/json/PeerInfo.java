/* Copyright 2015 Zürcher Hochschule der Angewandten Wissenschaften
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.zhaw.ficore.p2ds.group.json;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class PeerInfo {

    public final static int PEER_STATUS_ERROR = 2;

    public final static int PEER_STATUS_STARTED = 1;

    public final static int PEER_STATUS_STOPPED = 3;
    public final static int PEER_STATUS_UNKNOWN = 0;
    public final static int PEER_TYPE_INPUT = 1;
    public final static int PEER_TYPE_PRIVACY = 2;

    private long gid;
    private int lastStatus;
    private String peerName;
    private int peerType;
    private String publicKey;
    private String url;

    public long getGid() {
        return this.gid;
    }

    public int getLastStatus() {
        return this.lastStatus;
    }

    public String getPeerName() {
        return this.peerName;
    }

    public int getPeerType() {
        return this.peerType;
    }

    public String getPublicKey() {
        return this.publicKey;
    }

    public String getUrl() {
        return this.url;
    }

    public void setGid(final long gid) {
        this.gid = gid;
    }

    public void setLastStatus(final int lastStatus) {
        this.lastStatus = lastStatus;
    }

    public void setPeerName(final String peerName) {
        this.peerName = peerName;
    }

    public void setPeerType(final int peerType) {
        this.peerType = peerType;
    }

    public void setPublicKey(final String publicKey) {
        this.publicKey = publicKey;
    }

    public void setUrl(final String url) {
        this.url = url;
    }
}
