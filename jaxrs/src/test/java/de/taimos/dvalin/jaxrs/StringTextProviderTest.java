package de.taimos.dvalin.jaxrs;

/*
 * #%L
 * JAX-RS support for dvalin using Apache CXF
 * %%
 * Copyright (C) 2015 - 2016 Taimos GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import javax.ws.rs.core.MediaType;

import org.apache.cxf.jaxrs.provider.StringTextProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StringTextProviderTest {

    @Test
    void testWritable() throws Exception {
        StringTextProvider prov = new StringTextProvider();

        Assertions.assertTrue(prov.isWriteable(String.class, null, null, new MediaType("application", "octet-stream")));
        Assertions.assertTrue(prov.isWriteable(String.class, null, null, new MediaType("application", "pdf")));
        Assertions.assertTrue(prov.isWriteable(String.class, null, null, new MediaType("application", "foobar")));

        Assertions.assertFalse(prov.isWriteable(String.class, null, null, new MediaType("application", "json")));
        Assertions.assertFalse(prov.isWriteable(String.class, null, null, new MediaType("application", "foobar+json")));

        Assertions.assertFalse(prov.isWriteable(Object.class, null, null, new MediaType("application", "foobar")));
        Assertions.assertFalse(prov.isWriteable(Object.class, null, null, new MediaType("application", "pdf")));
    }
}
