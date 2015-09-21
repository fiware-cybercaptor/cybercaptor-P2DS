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

import java.security.SecureRandom;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@Entity(name = "TBL_REGISTRATION")
public class Registration {
    private static boolean test = false;

    public static boolean isTest() {
        return Registration.test;
    }

    public static void setTest(final boolean test) {
        Registration.test = test;
    }

    @Column(name = "ATTR_GID")
    private long gid;

    @Id
    @Column(name = "ATTR_REG_CODE")
    private String registrationCode;

    public Registration() {
        /* jaxb needs this one */
    }

    public Registration(final long gid) {
        if (!Group.exists(gid)) {
            throw new RuntimeException();
        }

        String chars = "abcdefghiklmnopqrstuwxyz0123456789";
        SecureRandom sr = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            int index = sr.nextInt(chars.length());

            sb.append(String.valueOf(chars.charAt(index)));
        }

        this.gid = gid;

        if (isTest()) {
            this.registrationCode = "TEST";
        } else {
            this.registrationCode = sb.toString();
        }
    }

    public long getGid() {
        return this.gid;
    }

    public String getRegistrationCode() {
        return this.registrationCode;
    }

    public void setGid(final long gid) {
        if (!Group.exists(gid)) {
            throw new RuntimeException();
        }
        this.gid = gid;
    }

    public void setRegistrationCode(final String registrationCode) {
        this.registrationCode = registrationCode;
    }
}
