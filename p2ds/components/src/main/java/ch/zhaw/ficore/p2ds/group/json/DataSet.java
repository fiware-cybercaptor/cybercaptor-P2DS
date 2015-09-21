package ch.zhaw.ficore.p2ds.group.json;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class DataSet {

    private String data;

    private String peerName;

    public String getData() {
        return this.data;
    }

    public String getPeerName() {
        return this.peerName;
    }

    public void setData(final String data) {
        this.data = data;
    }

    public void setPeerName(final String peerName) {
        this.peerName = peerName;
    }
}
