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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.xml.bind.annotation.XmlRootElement;

import ch.zhaw.ficore.p2ds.group.json.GroupConfigurationInfo;

@XmlRootElement
@Entity(name = "TBL_CONFIGURATION")
public class GroupConfiguration {
    public final static String MPC_PRC_ADDITIVE = "additive";

    public static GroupConfigurationInfo toGroupConfigurationInfo(
            final GroupConfiguration gc) {
        GroupConfigurationInfo gci = new GroupConfigurationInfo();
        gci.setField(gc.getField());
        gci.setMaxElement(gc.getMaxElement());
        gci.setMpcProtocol(gc.getMpcProtocol());
        gci.setNumberOfTimeSlots(gc.getNumberOfTimeSlots());
        gci.setNumberOfItems(gc.getNumberOfItems());
        gci.setResultBufferSize(gc.getResultBufferSize());
        return gci;
    }

    private String field;

    @Id
    @Column(name = "ATTR_GCID", nullable = false)
    private long gcid;

    @Column(name = "ATTR_GID", nullable = false, unique = true)
    private long gid;

    @Column(name = "ATTR_MAXELEMENT", nullable = false)
    private String maxElement;

    /* this is the protocol to be used (i.e. additive) */
    @Column(name = "ATTR_MPCPROTOCOL", nullable = false)
    private String mpcProtocol;

    @Column(name = "ATTR_NUMBEROFITEMS", nullable = false)
    private int numberOfItems;

    @Column(name = "ATTR_NUMBEROFTIMESLOTS", nullable = false)
    private int numberOfTimeSlots;

    @Column(name = "ATTR_RBSZ", nullable = false)
    private int resultBufferSize;

    public String getField() {
        return this.field;
    }

    public long getGid() {
        return this.gid;
    }

    public String getMaxElement() {
        return this.maxElement;
    }

    public String getMpcProtocol() {
        return this.mpcProtocol;
    }

    public int getNumberOfItems() {
        return this.numberOfItems;
    }

    public int getNumberOfTimeSlots() {
        return this.numberOfTimeSlots;
    }

    public int getResultBufferSize() {
        return this.resultBufferSize;
    }

    public void setField(final String field) {
        if (!field.matches("[0-9]+")) {
            throw new RuntimeException("Invalid value for field");
        }
        this.field = field;
    }

    public void setGid(final long gid) {
        if (!Group.exists(gid)) {
            throw new RuntimeException();
        }
        this.gid = gid;
    }

    public void setMaxElement(final String maxElement) {
        if (!maxElement.matches("[0-9]+")) {
            throw new RuntimeException("Invalid value for max element");
        }
        this.maxElement = maxElement;
    }

    public void setMpcProtocol(final String mpcProtocol) {
        this.mpcProtocol = mpcProtocol;
    }

    public void setNumberOfItems(final int numberOfItems) {
        this.numberOfItems = numberOfItems;
    }

    public void setNumberOfTimeSlots(final int numberOfTimeSlots) {
        this.numberOfTimeSlots = numberOfTimeSlots;
    }

    public void setResultBufferSize(final int resultBufferSize) {
        if (resultBufferSize <= 0) {
            this.resultBufferSize = 1;
        } else {
            this.resultBufferSize = resultBufferSize;
        }
    }
}
