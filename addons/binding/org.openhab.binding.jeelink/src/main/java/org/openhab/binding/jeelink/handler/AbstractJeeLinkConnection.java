/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.jeelink.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openhab.binding.jeelink.config.JeeLinkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for a connection to a JeeLink.
 * Manages ReadingListeners, finds out the sketch name and allows to propagate read lines.
 *
 * @author Volker Bier - Initial contribution
 */
public abstract class AbstractJeeLinkConnection implements JeeLinkConnection {
    private final Logger logger = LoggerFactory.getLogger(JeeLinkSerialConnection.class);

    protected final ArrayList<JeeLinkReadingConverter<?>> inputHandlers = new ArrayList<>();
    protected String sketchName = null;
    protected String port;

    private String[] initCommands;

    public AbstractJeeLinkConnection(String port) {
        this.port = port;
    }

    @Override
    public String getPort() {
        return port;
    }

    @Override
    public void addReadingConverter(JeeLinkReadingConverter<?> listener) {
        synchronized (inputHandlers) {
            if (!inputHandlers.contains(listener)) {
                logger.debug("Added listener {}.", listener);
                inputHandlers.add(listener);
            }
        }
    }

    @Override
    public void removeReadingConverters() {
        synchronized (inputHandlers) {
            inputHandlers.clear();
        }
    }

    public void propagateLine(String line) {
        logger.trace("Read line from port {}: {}", port, line);

        if (sketchName == null) {
            Matcher matcher = Pattern.compile("\\[(\\w+).*\\]").matcher(line);
            boolean matches = matcher.matches();
            if (matches) {
                sketchName = matcher.group(1);
                logger.debug("Sketch name found for port {}: {}", port, sketchName);

                initializeDevice();
            }
        }

        synchronized (inputHandlers) {
            for (JeeLinkReadingConverter<?> l : inputHandlers) {
                l.handleInput(line);
            }
        }
    }

    @Override
    public void setInitCommands(String initCommands) {
        if (initCommands != null && !initCommands.trim().isEmpty()) {
            this.initCommands = initCommands.split(";");
        }
    }

    private void initializeDevice() {
        try {
            if (initCommands != null) {
                logger.debug("Initializing device on port {} with commands {} ", port, Arrays.toString(initCommands));
                OutputStream initStream;
                initStream = getInitStream();
                if (initStream != null) {
                    try (OutputStreamWriter w = new OutputStreamWriter(initStream)) {
                        for (String cmd : initCommands) {
                            w.write(cmd);
                        }
                    }
                } else {
                    logger.warn("Connection on port {} did not provide an init stream for writing init commands", port);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to send init commands to port {}: {}", port, e.getMessage());
        }
    }

    @Override
    public String getSketchName() {
        return sketchName;
    }

    public static JeeLinkConnection createFor(JeeLinkConfig config) throws ConnectException {
        JeeLinkConnection connection;
        if (config.portName.startsWith("serial://")) {
            connection = new JeeLinkSerialConnection(config.portName.substring(9), 57600);
        } else if (config.portName.startsWith("tcp://")) {
            connection = new JeeLinkTcpConnection(config.portName.substring(6));
        } else {
            throw new ConnectException("Don't know how to open connection to " + config.portName);
        }

        return connection;
    }
}
