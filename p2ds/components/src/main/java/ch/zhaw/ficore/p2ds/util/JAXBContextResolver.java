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
package ch.zhaw.ficore.p2ds.util;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;

import ch.zhaw.ficore.p2ds.group.json.GroupInfo;
import ch.zhaw.ficore.p2ds.group.json.GroupMembers;
import ch.zhaw.ficore.p2ds.group.json.PeerInfo;

import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.api.json.JSONJAXBContext;

@Provider
public class JAXBContextResolver implements ContextResolver<JAXBContext> {
    private final JAXBContext context;
    private final Class<?>[] types = { GroupInfo.class, GroupMembers.class,
            PeerInfo.class };

    public JAXBContextResolver() throws Exception {
        this.context = new JSONJAXBContext(JSONConfiguration.natural().build(),
                this.types);
    }

    @Override
    public JAXBContext getContext(final Class<?> objectType) {
        for (Class<?> clazz : this.types) {
            if (clazz.equals(objectType)) {
                return this.context;
            }
        }

        return null;
    }
}