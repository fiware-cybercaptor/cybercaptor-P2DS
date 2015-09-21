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
public class GroupConfigurationInfo {
    private String field;

    private long gid;

    private String maxElement;

    private String mpcProtocol;

    private int numberOfItems;

    private int numberOfTimeSlots;

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
        this.field = field;
    }

    public void setGid(final long gid) {
        this.gid = gid;
    }

    public void setMaxElement(final String maxElement) {
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
        this.resultBufferSize = resultBufferSize;
    }

}
