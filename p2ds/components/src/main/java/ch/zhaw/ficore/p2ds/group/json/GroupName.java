package ch.zhaw.ficore.p2ds.group.json;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class GroupName {
    private String name;

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

}
