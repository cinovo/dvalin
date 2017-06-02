package de.taimos.dvalin.notification.push;

/*-
 * #%L
 * Dvalin notification service
 * %%
 * Copyright (C) 2016 - 2017 Taimos GmbH
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

import java.util.HashMap;
import java.util.Map;

public class GCMDataMessage extends PushMessage {
    
    private final Map<String, String> data = new HashMap<>();
    
    public GCMDataMessage(String message) {
        this.data.put("message", message);
    }
    
    public void addCustomData(String key, String value) {
        this.data.put(key, value);
    }
    
    @Override
    protected Map<String, Object> getPayload() {
        Map<String, Object> map = new HashMap<>();
        map.put("data", this.data);
        return map;
    }
    
    @Override
    public Platform getType() {
        return Platform.GCM;
    }
}
