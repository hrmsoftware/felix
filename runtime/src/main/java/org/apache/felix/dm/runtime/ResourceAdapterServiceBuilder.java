/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.dm.runtime;

import java.util.Dictionary;
import java.util.List;

import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.Service;
import org.osgi.framework.Bundle;

public class ResourceAdapterServiceBuilder extends ServiceComponentBuilder
{
    private final static String TYPE = "ResourceAdapterService";

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public void buildService(MetaData srvMeta, List<MetaData> depsMeta, Bundle b, DependencyManager dm) 
        throws Exception
    {
        String filter = srvMeta.getString(Params.filter, null);
        Class<?> implClass = b.loadClass(srvMeta.getString(Params.impl));
        String[] service = srvMeta.getStrings(Params.service, null);
        Dictionary<String, Object> properties = srvMeta.getDictionary(Params.properties, null);
        boolean propagate = "true".equals(srvMeta.getString(Params.propagate, "false"));
        String changed = srvMeta.getString(Params.changed, null /* no change callback if not specified explicitly */);
        Service srv = dm.createResourceAdapterService(filter, propagate, null, changed)
                        .setInterface(service, properties);       
        String factoryMethod = srvMeta.getString(Params.factoryMethod, null);
        if (factoryMethod == null)
        {
            srv.setImplementation(implClass);
        } 
        else
        {
            srv.setFactory(implClass, factoryMethod);
        }
        srv.setComposition(srvMeta.getString(Params.composition, null));
        ServiceLifecycleHandler lfcleHandler = new ServiceLifecycleHandler(srv, b, dm, srvMeta, depsMeta);
        // The dependencies will be plugged by our lifecycle handler.
        srv.setCallbacks(lfcleHandler, "init", "start", "stop", "destroy");
        // Adds dependencies (except named dependencies, which are managed by the lifecycle handler).
        addUnamedDependencies(b, dm, srv, srvMeta, depsMeta);
        dm.add(srv);
    }    
}
