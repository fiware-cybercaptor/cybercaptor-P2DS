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

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@Entity(name = "TBL_GROUP")
public class Group implements Serializable {

    /**
	 * 
	 */
    private static final long serialVersionUID = 1L;

    public static boolean exists(final long gid) {
        Group g = Manager.getEntityManager().find(Group.class, gid);
        return g != null;
    }

    @Id
    @Column(name = "ATTR_GID", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long gid;

    @Column(name = "ATTR_NAME", nullable = false)
    private String name;

    public Group() {
        this.name = null;
        this.gid = 0;
    }

    public long getGid() {
        return this.gid;
    }

    public String getName() {
        return this.name;
    }

    public void setGid(final long gid) {
        this.gid = gid;
    }

    public void setName(final String name) {
        this.name = name;
    }
}
