/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.jeelink.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads lines from TCP port and propagates them to registered InputListeners.
 *
 * @author Volker Bier - Initial contribution
 */
public class JeeLinkTcpConnection extends AbstractJeeLinkConnection {
    private static final Pattern IP_P = Pattern.compile("([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+):([0-9]+)");

    private final Logger logger = LoggerFactory.getLogger(JeeLinkTcpConnection.class);

    private Reader reader;
    private Socket socket;

    public JeeLinkTcpConnection(String port) {
        super(port);
    }

    @Override
    public synchronized void closeConnection() {
        if (reader != null) {
            logger.info("Closing TCP connection to port {}...", port);
            reader.close();
            reader = null;
            socket = null;
        }
    }

    @Override
    public synchronized void openConnection() throws ConnectException {
        if (reader != null) {
            logger.debug("TCP connection to port {} is already open!", port);
            return;
        }

        Matcher ipm = IP_P.matcher(port);
        if (!ipm.matches()) {
            throw new ConnectException("Invalid TCP port specification: " + port);
        }

        String hostName = ipm.group(1);
        int portNumber = Integer.parseInt(ipm.group(2));

        logger.info("Opening TCP connection to host {} port {}...", hostName, portNumber);
        try {
            logger.debug("Creating TCP socket to {}...", port);
            socket = new Socket(hostName, portNumber);
            logger.debug("TCP socket created.");
            reader = new Reader(socket);
            reader.start();
        } catch (IOException ex) {
            logger.error("Failed to create socket.", ex);
            throw new ConnectException(ex);
        }
    }

    @Override
    public OutputStream getInitStream() throws IOException {
        return socket == null ? null : socket.getOutputStream();
    }

    private class Reader extends Thread {
        private Socket socket;
        private BufferedReader inputReader;
        private volatile boolean isRunning = true;

        private Reader(Socket socket) throws IOException {
            this.socket = socket;
            inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        @Override
        public void run() {
            String line;
            logger.debug("Reader for TCP port {} starting...", port);
            try {
                while (isRunning) {
                    line = inputReader.readLine();

                    if (line == null) {
                        throw new IOException("Got EOF on port " + port);
                    }

                    propagateLine(line);
                }
            } catch (IOException ex) {
                if (isRunning) {
                    logger.error("Error reading from TCP port " + port + "!", ex);
                }
            }
            logger.debug("Reader for TCP port {} finished...", port);
        }

        public void close() {
            logger.debug("Shutting down reader for TCP port {}...", port);
            try {
                isRunning = false;
                socket.close();
                inputReader.close();
            } catch (IOException ex) {
                logger.error("Failed to close TCP port " + port + "!", ex);
            }
        }
    }
}
