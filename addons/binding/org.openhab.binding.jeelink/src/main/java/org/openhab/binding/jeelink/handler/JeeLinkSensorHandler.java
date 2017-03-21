/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.jeelink.handler;

import static org.openhab.binding.jeelink.JeeLinkBindingConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.jeelink.config.JeeLinkSensorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract thing handler for sensors connected to a JeeLink.
 *
 * @author Volker Bier - Initial contribution
 */
public abstract class JeeLinkSensorHandler<R extends Reading> extends BaseThingHandler {
    private Logger logger = LoggerFactory.getLogger(JeeLinkSensorHandler.class);

    private JeeLinkReadingListener<R> listener;
    private Class<R> clazz;

    private ScheduledFuture<?> valueUpdateJob;
    private ScheduledFuture<?> statusUpdateJob;

    private boolean initialized;

    public JeeLinkSensorHandler(Thing thing, Class<R> clazz) {
        super(thing);

        this.clazz = clazz;
    }

    @Override
    public synchronized void handleCommand(ChannelUID channelUid, Command command) {
        if (command instanceof StringType && channelUid.getIdWithoutGroup().equals(SENSOR_ID_CHANNEL)) {
            final String cmdName = command.toString();

            Matcher matcher = Pattern.compile("SetSensorId (\\w+)").matcher(cmdName);
            if (matcher.matches()) {
                final String oldId = getThing().getProperties().get(PROPERTY_SENSOR_ID);
                setNewSensorId(oldId, matcher.group(1));
            } else {
                logger.warn("Received invalid command {}!", cmdName);
            }
        } else if (!(command instanceof RefreshType)) {
            logger.warn("Thing {} ({}) channel {} does not support sending of commands!", getThing().getLabel(),
                    getThing().getUID(), channelUid.getId());
        }
    }

    private void setNewSensorId(String oldId, String newId) {
        ThingBuilder thingBuilder = editThing();
        Map<String, String> map = new HashMap<>();
        map.put(PROPERTY_SENSOR_ID, newId);
        thingBuilder.withProperties(map);
        updateThing(thingBuilder.build());

        if (initialized) {
            JeeLinkHandler jlh = (JeeLinkHandler) getBridge().getHandler();
            JeeLinkReadingConverter<R> c = jlh.getConverter(clazz);
            c.removeReadingListener(listener);
            c.addReadingListener(newId, listener);
        }
        logger.info("Thing {} ({}) updated sensor id from {} to {}", getThing().getLabel(), getThing().getUID(), oldId,
                newId);
    }

    @Override
    public synchronized void initialize() {
        if (initialized) {
            logger.info("JeeLink sensor handler for thing {} ({}) is already initialized", getThing().getLabel(),
                    getThing().getUID());
            return;
        }

        statusUpdateJob = createStatusUpdateJob();
        final int updateInterval = getConfig().as(JeeLinkSensorConfig.class).updateInterval;
        if (updateInterval > 0) {
            valueUpdateJob = createUpdateJob(updateInterval);
        }

        final int bufferSize = getConfig().as(JeeLinkSensorConfig.class).bufferSize;
        String idStr = getConfig().as(JeeLinkSensorConfig.class).sensorId;
        try {
            updateStatus(ThingStatus.OFFLINE);

            Average<R> avg = null;
            if (bufferSize > 0) {
                logger.debug("Using rolling average with buffer size {}...", bufferSize);
                avg = new RollingReadingAverage<>(clazz, bufferSize);
            }

            logger.debug("Adding reading listener for id {}...", idStr);
            listener = new JeeLinkReadingListener<R>(avg) {
                @Override
                public synchronized void handleReading(R reading) {
                    try {
                        if (isReadingWithinBounds(reading)) {
                            boolean initial = lastReading == null;

                            // propagate initial reading
                            if (initial) {
                                updateReadingStates(reading);
                            }
                            super.handleReading(reading);

                            // propagate every reading in live mode
                            if (!initial && updateInterval == 0) {
                                updateReadingStates(getCurrentReading());
                            }
                        }

                        // make sure status is online as soon as we get a reading
                        updateStatus(ThingStatus.ONLINE);
                    } catch (Throwable th) {
                        logger.error("Uncaught throwable in JeeLink sensor handler for thing " + getThing().getLabel()
                                + " (" + getThing().getUID() + ")...", th);
                    }
                }
            };

            JeeLinkHandler jlh = (JeeLinkHandler) getBridge().getHandler();
            JeeLinkReadingConverter<R> c = jlh.getConverter(clazz);
            c.addReadingListener(idStr, listener);
            initialized = true;
        } catch (NumberFormatException ex) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Thing has an invalid sensor id: " + idStr);
            logger.info("JeeLink sensor handler for thing {} ({}) is OFFLINE", getThing().getLabel(),
                    getThing().getUID());
        }
    }

    private ScheduledFuture<?> createUpdateJob(final int updateInterval) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                logger.trace("JeeLink sensor cyclic propagation for thing {} ({})...", getThing().getLabel(),
                        getThing().getUID());

                if (listener != null) {
                    try {
                        updateReadingStates(listener.getCurrentReading());
                    } catch (Throwable th) {
                        logger.error("Uncaught throwable in JeeLink sensor handler for thing " + getThing().getLabel()
                                + " (" + getThing().getUID() + ")...", th);
                    }
                }
            }
        };
        logger.debug("JeeLink sensor handler value propagation job for thing {} ({}) created with interval {} s",
                getThing().getLabel(), getThing().getUID(), updateInterval);
        return scheduler.scheduleAtFixedRate(runnable, updateInterval, updateInterval, TimeUnit.SECONDS);
    }

    private ScheduledFuture<?> createStatusUpdateJob() {
        final int sensorTimeout = getConfig().as(JeeLinkSensorConfig.class).sensorTimeout;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (listener != null && getThing().getStatus() == ThingStatus.ONLINE
                        && (listener.getLastReadingTime() == -1
                                || System.currentTimeMillis() - listener.getLastReadingTime() > sensorTimeout * 1000)) {
                    logger.debug("Setting JeeLink sensor handler status for thing {} ({}) to OFFLINE",
                            getThing().getLabel(), getThing().getUID());
                    updateStatus(ThingStatus.OFFLINE);
                }
            }
        };
        logger.debug("JeeLink sensor timeout job for thing {} ({}) created with interval {} s", getThing().getLabel(),
                getThing().getUID(), sensorTimeout);
        return scheduler.scheduleAtFixedRate(runnable, sensorTimeout, 1, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void dispose() {
        if (initialized) {
            JeeLinkHandler jlh = (JeeLinkHandler) getBridge().getHandler();
            JeeLinkReadingConverter<R> c = jlh.getConverter(clazz);
            c.removeReadingListener(listener);
            listener = null;

            if (valueUpdateJob != null) {
                valueUpdateJob.cancel(true);
                valueUpdateJob = null;
            }
            if (statusUpdateJob != null) {
                statusUpdateJob.cancel(true);
                statusUpdateJob = null;
            }

            initialized = false;

            super.dispose();
        }
    }

    /**
     * Override to add reading validation.
     *
     * @param reading the reading to validate.
     * @return whether the reading is valid.
     */
    public boolean isReadingWithinBounds(R reading) {
        return true;
    }

    public abstract void updateReadingStates(R reading);
}
