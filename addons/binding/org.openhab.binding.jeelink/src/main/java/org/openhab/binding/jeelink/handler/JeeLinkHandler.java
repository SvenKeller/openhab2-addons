/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.jeelink.handler;

import java.util.ArrayList;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.BridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.jeelink.config.JeeLinkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for a JeeLink USB Receiver thing.
 *
 * @author Volker Bier - Initial contribution
 */
public class JeeLinkHandler extends BaseBridgeHandler implements BridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(JeeLinkHandler.class);

    private JeeLinkConnection connection;
    private ArrayList<JeeLinkReadingConverter<?>> converters;

    public JeeLinkHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        try {
            JeeLinkConfig cfg = getConfig().as(JeeLinkConfig.class);
            connection = AbstractJeeLinkConnection.createFor(cfg);
            connection.openConnection();

            String initCommands = cfg.initCommands;
            if (initCommands != null && !initCommands.trim().isEmpty()) {
                logger.info("Setting init commands for port {}: {}", connection.getPort(), initCommands);
                connection.setInitCommands(initCommands);
            }

            converters = SensorDefinition.createConverters(this, cfg.sketchName);
            for (JeeLinkReadingConverter<?> cnv : converters) {
                connection.addReadingConverter(cnv);
            }
            logger.info("updating JeeLinkHandler for port {} to be ONLINE...", connection.getPort());
            updateStatus(ThingStatus.ONLINE);
            logger.info("JeeLinkHandler for port {} is ONLINE.", connection.getPort());
        } catch (Exception ex) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, ex.getMessage());
            logger.info("JeeLinkHandler for port {} is OFFLINE.", connection.getPort());
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUid, Command command) {
        logger.error("Thing {} channel {} does not support sending of commands!", getThing().getUID(),
                channelUid.getId());
    }

    @Override
    public void dispose() {
        if (connection != null) {
            connection.removeReadingConverters();
            connection.closeConnection();
        }

        SensorDefinition.disposeConverters(this);

        super.dispose();
    }

    public <R extends Reading<R>> JeeLinkReadingConverter<R> getConverter(Class<R> clazz) {
        ArrayList<JeeLinkReadingConverter<R>> cs = new ArrayList<>();

        for (JeeLinkReadingConverter<?> c : converters) {
            if (c.convertsTo(clazz)) {
                return (JeeLinkReadingConverter<R>) c;
            }
        }

        return null;
    }
}
