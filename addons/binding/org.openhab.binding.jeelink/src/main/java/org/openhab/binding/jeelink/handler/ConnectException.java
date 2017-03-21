/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.jeelink.handler;

/**
 * Exception connecting to a JeeLink.
 *
 * @author Volker Bier - Initial contribution
 */
public class ConnectException extends Exception {
    public ConnectException(Throwable cause) {
        super(cause);
    }

    public ConnectException(String message) {
        super(message);
    }
}
