package ch.zhaw.ficore.p2ds.peer.storage;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.Lob;

import ch.zhaw.ficore.p2ds.peers.json.PeerConfigurationInfo;

@Entity(name = "TBL_PEERCONFIGURATION")
public class PeerConfiguration {

    public static PeerConfigurationInfo find(final String peerName) {
        EntityManager em = Manager.getEntityManager();
        PeerConfiguration pc = em.find(PeerConfiguration.class, peerName);
        em.close();
        if (pc == null) {
            return null;
        }

        return pc.toPeerConfigurationInfo();
    }

    public static PeerConfiguration findRaw(final String peerName) {
        EntityManager em = Manager.getEntityManager();
        PeerConfiguration pc = em.find(PeerConfiguration.class, peerName);
        em.close();

        return pc;
    }

    @Lob
    @Column(name = "ATTR_FINALRESULTSURL", nullable = false)
    private String finalResultsURL;

    @Column(name = "ATTR_GROUP_MGMT_URL", nullable = false)
    private String groupMgmtURL;

    @Id
    @Column(name = "ATTR_NAME", nullable = false)
    private String name;

    @Column(name = "ATTR_TYPE", nullable = false)
    private int peerType;

    @Lob
    @Column(name = "ATTR_PRIVATE_KEY", nullable = false)
    private String privateKey; // b64 encoded

    @Lob
    @Column(name = "ATTR_PUBLIC_KEY", nullable = false)
    private String publicKey; // b64 encoded

    @Column(name = "ATTR_REGISTRATION_CODE", nullable = false)
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

    public PeerConfigurationInfo toPeerConfigurationInfo() {
        PeerConfigurationInfo pci = new PeerConfigurationInfo();
        pci.setGroupMgmtURL(getGroupMgmtURL());
        pci.setName(getName());
        pci.setPrivateKey(getPrivateKey());
        pci.setPublicKey(getPublicKey());
        pci.setRegistrationCode(getRegistrationCode());
        pci.setPeerType(getPeerType());
        pci.setFinalResultsURL(getFinalResultsURL());
        return pci;
    }
}
