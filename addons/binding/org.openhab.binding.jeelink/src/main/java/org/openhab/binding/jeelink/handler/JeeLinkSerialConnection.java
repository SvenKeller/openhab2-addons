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
import java.util.TooManyListenersException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

/**
 * Reads lines from serial port and propagates them to registered InputListeners.
 *
 * @author Volker Bier - Initial contribution
 */
public class JeeLinkSerialConnection extends AbstractJeeLinkConnection {
    private final Logger logger = LoggerFactory.getLogger(JeeLinkSerialConnection.class);

    private int baudRate;

    private SerialPort serialPort;
    private boolean open;

    public JeeLinkSerialConnection(String portName, int baudRate) {
        super(portName);

        logger.info("Creating serial connection for port {} with baud rate {}...", portName, baudRate);
        this.baudRate = baudRate;
    }

    @Override
    public synchronized void closeConnection() {
        if (open) {
            logger.info("Closing serial connection to port {}...", port);

            serialPort.notifyOnDataAvailable(false);
            serialPort.removeEventListener();

            serialPort.close();
            open = false;
        } else {
            logger.debug("Serial connection to port {} is already closed or has active listeners!", port);
        }
    }

    @Override
    public synchronized void openConnection() throws ConnectException {
        try {
            if (!open) {
                logger.info("Opening serial connection to port {} with baud rate {}...", port, baudRate);

                CommPortIdentifier portIdentifier;

                try {
                    portIdentifier = CommPortIdentifier.getPortIdentifier(port);
                    serialPort = portIdentifier.open("openhab", 3000);
                    open = true;

                    serialPort.setSerialPortParams(baudRate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                            SerialPort.PARITY_NONE);

                    final BufferedReader input = new BufferedReader(new InputStreamReader(serialPort.getInputStream()));

                    serialPort.addEventListener(new SerialPortEventListener() {
                        @Override
                        public void serialEvent(SerialPortEvent event) {
                            try {
                                if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
                                    propagateLine(input.readLine());
                                }
                            } catch (IOException ex) {
                                logger.error("IOException reading from port {}!", port);
                            }
                        }
                    });

                    serialPort.notifyOnDataAvailable(true);
                } catch (UnsupportedCommOperationException | IOException | TooManyListenersException ex) {
                    closeConnection();
                    throw ex;
                }
            } else {
                logger.debug("Serial connection to port {} is already open!", port);
            }
        } catch (NoSuchPortException | PortInUseException | UnsupportedCommOperationException | IOException
                | TooManyListenersException ex) {
            throw new ConnectException(ex);
        }
    }

    @Override
    public OutputStream getInitStream() throws IOException {
        return open ? serialPort.getOutputStream() : null;
    }
}
