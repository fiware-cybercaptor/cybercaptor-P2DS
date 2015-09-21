package ch.zhaw.ficore.p2ds.group.json;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class DataSets {
    private List<String> data;

    private String peerName;

    public List<String> getData() {
        return this.data;
    }

    public String getPeerName() {
        return this.peerName;
    }

    public void setData(final List<String> data) {
        this.data = data;
    }

    public void setPeerName(final String peerName) {
        this.peerName = peerName;
    }
}
