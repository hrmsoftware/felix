package org.apache.felix.dependencymanager.samples.device;

import java.util.Hashtable;

import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;

/**
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class Activator extends DependencyActivatorBase {
    @Override
    public void init(BundleContext context, DependencyManager dm) throws Exception { 
        createDeviceAndParameter(dm, 1);
        createDeviceAndParameter(dm, 2);

        dm.add(createAdapterService(Device.class, null)
            .setImplementation(DeviceConsumer.class));
    }
    
    private void createDeviceAndParameter(DependencyManager dm, int id) {
        Hashtable<String, Object> props = new Hashtable<>();
        props.put("device.id", id);
        dm.add(createComponent()
            .setImplementation(new DeviceImpl(id)).setInterface(Device.class.getName(), props));
           
        props = new Hashtable<>();
        props.put("device.id", id);
        dm.add(createComponent()
            .setImplementation(new DeviceParameterImpl(id)).setInterface(DeviceParameter.class.getName(), props));        
    }
}
