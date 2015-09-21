package ch.zhaw.ficore.p2ds.peers.json;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class PeerConfigurationInfo {
    private String finalResultsURL;

    private String groupMgmtURL;

    private String name;

    private int peerType;

    private String privateKey; // b64 encoded

    private String publicKey; // b64 encoded

    private String registrationCode;

    public String getFinalResultsURL() {
        return this.finalResultsURL;
    }

    public String getGroupMgmtURL() {
        return this.groupMgmtURL;
    }

    public String getName() {
        return this.name;
    }

    public int getPeerType() {
        return this.peerType;
    }

    public String getPrivateKey() {
        return this.privateKey;
    }

    public String getPublicKey() {
        return this.publicKey;
    }

    public String getRegistrationCode() {
        return this.registrationCode;
    }

    public void setFinalResultsURL(final String finalResultsURL) {
        this.finalResultsURL = finalResultsURL;
    }

    public void setGroupMgmtURL(final String groupMgmtURL) {
        this.groupMgmtURL = groupMgmtURL;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setPeerType(final int peerType) {
        this.peerType = peerType;
    }

    public void setPrivateKey(final String privateKey) {
        this.privateKey = privateKey;
    }

    public void setPublicKey(final String publicKey) {
        this.publicKey = publicKey;
    }

    public void setRegistrationCode(final String registrationCode) {
        this.registrationCode = registrationCode;
    }
}
