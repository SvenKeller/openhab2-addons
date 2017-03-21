/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.jeelink.internal;

import static org.openhab.binding.jeelink.JeeLinkBindingConstants.*;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.openhab.binding.jeelink.discovery.SensorDiscoveryService;
import org.openhab.binding.jeelink.handler.JeeLinkHandler;
import org.openhab.binding.jeelink.handler.SensorDefinition;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link JeeLinkHandlerFactory} is responsible for creating things and thing handlers.
 *
 * @author Volker Bier - Initial contribution
 */
public class JeeLinkHandlerFactory extends BaseThingHandlerFactory {
    private final Logger logger = LoggerFactory.getLogger(JeeLinkHandlerFactory.class);

    private Map<ThingUID, ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUid) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUid);
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUid = thing.getThingTypeUID();
        ThingHandler handler = null;

        if (thingTypeUid.equals(JEELINK_STICK_THING_TYPE)) {
            final String sketchName = (String) thing.getConfiguration().getProperties().get(PROPERTY_SKETCH_NAME);
            logger.info("creating JeeLinkHandler with sketch {} for thing {}...", sketchName, thing.getUID().getId());

            JeeLinkHandler jlh = new JeeLinkHandler((Bridge) thing);
            handler = jlh;

            registerSensorDiscoveryService(jlh);
        } else {
            handler = SensorDefinition.createHandler(thingTypeUid, thing);

            if (handler == null) {
                logger.warn("skipping creation of unknown handler for thing {} with type {}...", thing.getUID().getId(),
                        thing.getThingTypeUID().getId());
            }
        }

        return handler;
    }

    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof JeeLinkHandler) {
            ServiceRegistration<?> serviceReg = this.discoveryServiceRegs.get(thingHandler.getThing().getUID());
            if (serviceReg != null) {
                serviceReg.unregister();
                discoveryServiceRegs.remove(thingHandler.getThing().getUID());
            }
        }
    }

    private synchronized void registerSensorDiscoveryService(JeeLinkHandler bridgeHandler) {
        logger.info("registering sensor discovery service...");
        SensorDiscoveryService discoveryService = new SensorDiscoveryService(bridgeHandler);
        discoveryServiceRegs.put(bridgeHandler.getThing().getUID(), bundleContext
                .registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<String, Object>()));
    }
}
