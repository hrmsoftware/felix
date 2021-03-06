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
package org.apache.felix.dm.impl;

import java.lang.reflect.InvocationTargetException;
import java.util.Dictionary;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.ConfigurationDependency;
import org.apache.felix.dm.Logger;
import org.apache.felix.dm.PropertyMetaData;
import org.apache.felix.dm.context.AbstractDependency;
import org.apache.felix.dm.context.DependencyContext;
import org.apache.felix.dm.context.Event;
import org.apache.felix.dm.context.EventType;
import org.apache.felix.dm.impl.metatype.MetaTypeProviderImpl;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

/**
 * Implementation for a configuration dependency.
 * 
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class ConfigurationDependencyImpl extends AbstractDependency<ConfigurationDependency> implements ConfigurationDependency, ManagedService {
    private Dictionary<String, Object> m_settings;
	private String m_pid;
	private ServiceRegistration m_registration;
	private volatile Class<?> m_configType;
    private MetaTypeProviderImpl m_metaType;
	private final AtomicBoolean m_updateInvokedCache = new AtomicBoolean();
	private final Logger m_logger;
	private final BundleContext m_context;
	private boolean m_needsInstance = true;
	private final static int UPDATE_MAXWAIT = 30000; // max time to wait until a component has handled a configuration change event.

    public ConfigurationDependencyImpl() {
        this(null, null);
    }
	
    public ConfigurationDependencyImpl(BundleContext context, Logger logger) {
        m_context = context;
    	m_logger = logger;
        setRequired(true);
        setCallback("updated");
    }
    
	public ConfigurationDependencyImpl(ConfigurationDependencyImpl prototype) {
	    super(prototype);
	    m_context = prototype.m_context;
	    m_pid = prototype.m_pid;
	    m_logger = prototype.m_logger;
        m_metaType = prototype.m_metaType != null ? new MetaTypeProviderImpl(prototype.m_metaType, this, null) : null;
        m_needsInstance = prototype.needsInstance();
        m_configType = prototype.m_configType;
	}
	
    @Override
    public Class<?> getAutoConfigType() {
        return null; // we don't support auto config mode.
    }

	@Override
	public DependencyContext createCopy() {
	    return new ConfigurationDependencyImpl(this);
	}

	/**
	 * Sets a callback method invoked on the instantiated component.
	 */
    public ConfigurationDependencyImpl setCallback(String callback) {
        super.setCallbacks(callback, null);
        return this;
    }
    
    /**
     * Sets a callback method on an external callback instance object.
     * The component is not yet instantiated at the time the callback is invoked.
     * We check if callback instance is null, in this case, the callback will be invoked on the instantiated component.
     */
    public ConfigurationDependencyImpl setCallback(Object instance, String callback) {
        boolean needsInstantiatedComponent = (instance == null);
    	return setCallback(instance, callback, needsInstantiatedComponent);
    }

    /**
     * Sets a callback method on an external callback instance object.
     * If needsInstance == true, the component is instantiated at the time the callback is invoked.
     * We check if callback instance is null, in this case, the callback will be invoked on the instantiated component.
     */
    public ConfigurationDependencyImpl setCallback(Object instance, String callback, boolean needsInstance) {
        super.setCallbacks(instance, callback, null);
        m_needsInstance = needsInstance;
        return this;
    }
        
    /**
     * Sets a type-safe callback method invoked on the instantiated component.
     */
    public ConfigurationDependency setCallback(String callback, Class<?> configType) {
        setCallback(callback);
        m_configType = configType;
        return this;
    }

    /**
     * Sets a type-safe callback method on an external callback instance object.
     * The component is not yet instantiated at the time the callback is invoked.
     */
    public ConfigurationDependency setCallback(Object instance, String callback, Class<?> configType) {
        setCallback(instance, callback);
        m_configType = configType;
        return this;
    }
    
    /**
     * Sets a type-safe callback method on an external callback instance object.
     * If needsInstance == true, the component is instantiated at the time the callback is invoked.
     */
    public ConfigurationDependencyImpl setCallback(Object instance, String callback, Class<?> configType, boolean needsInstance) {
        setCallback(instance, callback, needsInstance);
        m_configType = configType;
        return this;
    }
    
    /**
     * This method indicates to ComponentImpl if the component must be instantiated when this Dependency is started.
     * If the callback has to be invoked on the component instance, then the component
     * instance must be instantiated at the time the Dependency is started because when "CM" calls ConfigurationDependencyImpl.updated()
     * callback, then at this point we have to synchronously delegate the callback to the component instance, and re-throw to CM
     * any exceptions (if any) thrown by the component instance updated callback.
     */
    @Override
    public boolean needsInstance() {
        return m_needsInstance;
    }

    @Override
    public void start() {
        BundleContext context = m_component.getBundleContext();
        if (context != null) { // If null, we are in a test environment
	        Properties props = new Properties();
	        props.put(Constants.SERVICE_PID, m_pid);
	        ManagedService ms = this;
	        if (m_metaType != null) {
	            ms = m_metaType;
	        }
	        m_registration = context.registerService(ManagedService.class.getName(), ms, props);
        }
        super.start();
    }

    @Override
    public void stop() {
        if (m_registration != null) {
            try {
                m_registration.unregister();
            } catch (IllegalStateException e) {}
        	m_registration = null;
        }
        super.stop();
    }
    
	public ConfigurationDependency setPid(String pid) {
		ensureNotActive();
		m_pid = pid;
		return this;
	}
		
    @Override
    public String getSimpleName() {
        return m_pid;
    }
    
    @Override
    public String getFilter() {
        return null;
    }

    public String getType() {
        return "configuration";
    }
            
    public ConfigurationDependency add(PropertyMetaData properties)
    {
        createMetaTypeImpl();
        m_metaType.add(properties);
        return this;
    }

    public ConfigurationDependency setDescription(String description)
    {
        createMetaTypeImpl();
        m_metaType.setDescription(description);
        return this;
    }

    public ConfigurationDependency setHeading(String heading)
    {
        createMetaTypeImpl();
        m_metaType.setName(heading);
        return this;
    }
    
    public ConfigurationDependency setLocalization(String path)
    {
        createMetaTypeImpl();
        m_metaType.setLocalization(path);
        return this;
    }
    
	@SuppressWarnings("unchecked")
	@Override
	public Dictionary<String, Object> getProperties() {
		if (m_settings == null) {
            throw new IllegalStateException("cannot find configuration");
		}
		return m_settings;
	}
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void updated(final Dictionary settings) throws ConfigurationException {
    	m_updateInvokedCache.set(false);
        Dictionary<String, Object> oldSettings = null;
        synchronized (this) {
            oldSettings = m_settings;
        }

        if (oldSettings == null && settings == null) {
            // CM has started but our configuration is not still present in the CM database: ignore
            return;
        }

        // If this is initial settings, or a configuration update, we handle it synchronously.
        // We'll conclude that the dependency is available only if invoking updated did not cause
        // any ConfigurationException.
        // However, we still want to schedule the event in the component executor, to make sure that the
        // callback is invoked safely. So, we use a Callable and a FutureTask that allows to handle the 
        // configuration update through the component executor. We still wait for the result because
        // in case of any configuration error, we have to return it from the current thread.
        
        Callable<ConfigurationException> result = new Callable<ConfigurationException>() {
            @Override
            public ConfigurationException call() throws Exception {
                try {
                    invokeUpdated(settings); // either the callback instance or the component instances, if available.
                } catch (ConfigurationException e) {
                    return e;
                }
                return null;
            }            
        };
        
        // Schedule the configuration update in the component executor. In Normal case, the task is immediately executed.
        // But in a highly concurrent system, and if the component is being reconfigured, the component may be currently busy
        // (handling a service dependency event for example), so the task will be enqueued in the component executor, and
        // we'll wait for the task execution by using a FutureTask:
        
        FutureTask<ConfigurationException> ft = new FutureTask<>(result);
        m_component.getExecutor().execute(ft);
        
        try {
            ConfigurationException confError = ft.get(UPDATE_MAXWAIT, TimeUnit.MILLISECONDS);
            if (confError != null) {
                throw confError; // will be logged by the Configuration Admin service;
            }
          }

        catch (ExecutionException error) {
            throw new ConfigurationException(null, "Configuration update error, unexpected exception.", error);
        } catch (InterruptedException error) {
            // will be logged by the Configuration Admin service;
            throw new ConfigurationException(null, "Configuration update interrupted.", error);
        } catch (TimeoutException error) {
            // will be logged by the Configuration Admin service;
            throw new ConfigurationException(null, "Component did not handle configuration update timely.", error);
        }
        
        // At this point, we have accepted the configuration.
        synchronized (this) {
            m_settings = settings;
        }

        if ((oldSettings == null) && (settings != null)) {
            // Notify the component that our dependency is available.
            m_component.handleEvent(this, EventType.ADDED, new ConfigurationEventImpl(m_pid, settings));
        }
        else if ((oldSettings != null) && (settings != null)) {
            // Notify the component that our dependency has changed.
            m_component.handleEvent(this, EventType.CHANGED, new ConfigurationEventImpl(m_pid, settings));
        }
        else if ((oldSettings != null) && (settings == null)) {
            // Notify the component that our dependency has been removed.
            // Notice that the component will be stopped, and then all required dependencies will be unbound
            // (including our configuration dependency).
            m_component.handleEvent(this, EventType.REMOVED, new ConfigurationEventImpl(m_pid, oldSettings));
        }
    }

    @Override
    public void invokeCallback(EventType type, Event ... event) {
        switch (type) {
        case ADDED:
            try {
                invokeUpdated(m_settings);
            } catch (ConfigurationException e) {
                logConfigurationException(e);
            }
            break;
        case CHANGED:
            // We already did that synchronously, from our updated method
            break;
        case REMOVED:
            // The state machine is stopping us. We have to invoke updated(null).
            // Reset for the next time the state machine calls invokeCallback(ADDED)
            m_updateInvokedCache.set(false);
            break;
        default:
            break;
        }
    }
    
    /**
     * Creates the various signatures and arguments combinations used for the configuration-type style callbacks.
     * 
     * @param service the service for which the callback should be applied;
     * @param configType the configuration type to use (can be <code>null</code>);
     * @param settings the actual configuration settings.
     */
    static CallbackTypeDef createCallbackType(Logger logger, Component service, Class<?> configType, Dictionary<?, ?> settings) {
        Class<?>[][] sigs = new Class[][] { { Dictionary.class }, { Component.class, Dictionary.class }, {} };
        Object[][] args = new Object[][] { { settings }, { service, settings }, {} };

        if (configType != null) {
            try {
                // if the configuration is null, it means we are losing it, and since we pass a null dictionary for other callback
                // (that accepts a Dictionary), then we should have the same behavior and also pass a null conf proxy object when
                // the configuration is lost.
                Object configurable = settings != null ? Configurable.create(configType, settings) : null;
                
                logger.debug("Using configuration-type injecting using %s as possible configType.", configType.getSimpleName());

                sigs = new Class[][] { { Dictionary.class }, { Component.class, Dictionary.class }, { Component.class, configType }, { configType }, {} };
                args = new Object[][] { { settings }, { service, settings }, { service, configurable }, { configurable }, {} };
            }
            catch (Exception e) {
                // This is not something we can recover from, use the defaults above...
                logger.warn("Failed to create configurable for configuration type %s!", e, configType);
            }
        }

        return new CallbackTypeDef(sigs, args);
    }

    private void invokeUpdated(Dictionary<?, ?> settings) throws ConfigurationException {
        if (m_updateInvokedCache.compareAndSet(false, true)) {
            Object[] instances = super.getInstances(); // either the callback instance or the component instances
            if (instances == null) {
                return;
            }

            CallbackTypeDef callbackInfo = createCallbackType(m_logger, m_component, m_configType, settings);

            for (int i = 0; i < instances.length; i++) {
                try {
                    InvocationUtil.invokeCallbackMethod(instances[i], m_add, callbackInfo.m_sigs, callbackInfo.m_args);
                }
                catch (InvocationTargetException e) {
                    // The component has thrown an exception during it's callback invocation.
                    if (e.getTargetException() instanceof ConfigurationException) {
                        // the callback threw an OSGi ConfigurationException: just re-throw it.
                        throw (ConfigurationException) e.getTargetException();
                    }
                    else {
                        // wrap the callback exception into a ConfigurationException.
                        throw new ConfigurationException(null, "Configuration update failed", e.getTargetException());
                    }
                }
                catch (NoSuchMethodException e) {
                    // if the method does not exist, ignore it
                }
                catch (Throwable t) {
                    // wrap any other exception as a ConfigurationException.
                    throw new ConfigurationException(null, "Configuration update failed", t);
                }
            }
        }
    }
    
    private synchronized void createMetaTypeImpl() {
        if (m_metaType == null) {
            m_metaType = new MetaTypeProviderImpl(m_pid, m_context, m_logger, this, null);
        }
    }
    
    private void logConfigurationException(ConfigurationException e) {
        if (m_logger != null) {
            m_logger.log(Logger.LOG_ERROR, "Got exception while handling configuration update for pid " + m_pid, e);
        }
    }
}
