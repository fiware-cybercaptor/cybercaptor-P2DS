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
package ch.zhaw.ficore.p2ds.group.storage;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.TypedQuery;
import javax.xml.bind.annotation.XmlRootElement;

import ch.zhaw.ficore.p2ds.group.json.PeerInfo;

@XmlRootElement
@Entity(name = "TBL_PEER")
public class Peer {

    public static boolean exists(final long pid) {
        Peer p = Manager.getEntityManager().find(Peer.class, pid);
        return p != null;
    }

    public static Peer find(final EntityManager em, final String peerName) {
        TypedQuery<Peer> peers = em.createQuery(
                "SELECT c FROM TBL_PEER c WHERE c.peerName = :peerName",
                Peer.class);

        peers.setParameter("peerName", peerName);

        List<Peer> r = peers.getResultList();

        if (r == null) {
            return null;
        }

        if (r.size() < 1) {
            return null;
        }

        return r.get(0);
    }

    public static PeerInfo toPeerInfo(final Peer p) {
        PeerInfo pi = new PeerInfo();
        pi.setUrl(p.getUrl());
        pi.setPublicKey(p.getPublicKey());
        pi.setGid(p.getGid());
        pi.setPeerType(p.getPeerType());
        pi.setPeerName(p.getPeerName());
        return pi;
    }

    @Column(name = "ATTR_GID", nullable = false)
    private long gid;

    @Column(name = "ATTR_LAST_STATUS", nullable = false)
    private int lastStatus;

    @Column(name = "ATTR_PEER_NAME", nullable = false, unique = true)
    private String peerName;

    @Column(name = "ATTR_PEER_TYPE", nullable = false)
    private int peerType;

    @Id
    @Column(name = "ATTR_PID", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long pid;

    @Lob
    @Column(name = "ATTR_PUB_KEY", nullable = true)
    private String publicKey; // Base64 encoded cert

    @Column(name = "ATTR_REG_CODE", nullable = false)
    private String registrationCode;

    @Column(name = "ATTR_URL", nullable = false)
    private String url;

    @Column(name = "ATTR_VERIFIED", nullable = false)
    private boolean verified;

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

    public long getPid() {
        return this.pid;
    }

    public String getPublicKey() {
        return this.publicKey;
    }

    public String getRegistrationCode() {
        return this.registrationCode;
    }

    public String getUrl() {
        return this.url;
    }

    public boolean isVerified() {
        return this.verified;
    }

    public void setGid(final long gid) {
        if (!Group.exists(gid)) {
            throw new RuntimeException();
        }

        this.gid = gid;
    }

    public void setLastStatus(final int lastStatus) {
        this.lastStatus = lastStatus;
    }

    public void setPeerName(final String peerName) {
        this.peerName = peerName;
    }

    public void setPeerType(final int peerType) {
        if (peerType != PeerInfo.PEER_TYPE_INPUT
                && peerType != PeerInfo.PEER_TYPE_PRIVACY) {
            throw new RuntimeException("Invalid peer type:" + peerType);
        }
        this.peerType = peerType;
    }

    public void setPid(final long pid) {
        this.pid = pid;
    }

    public void setPublicKey(final String publicKey) {
        this.publicKey = publicKey;
    }

    public void setRegistrationCode(final String registrationCode) {
        this.registrationCode = registrationCode;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public void setVerified(final boolean verified) {
        this.verified = verified;
    }
}
