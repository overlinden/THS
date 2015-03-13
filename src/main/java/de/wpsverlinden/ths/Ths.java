/*
 *   THS - A tiny http server
 *   Copyright (C) 2012  Oliver Verlinden (http://wps-verlinden.de)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.wpsverlinden.ths;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Ths {

    private static final Logger log = Logger.getLogger(Ths.class.getName());

    public static void main(String[] args) {

        log.log(Level.INFO, "ths 1.0 - Tiny HTTP Server\nwritten by Oliver Verlinden (http://wps-verlinden.de)\n");
        // parse arguments
        if (args.length != 1) {
            log.log(Level.SEVERE, "Invalid numer of parameters. Usage: ths <port>");
            System.exit(-1);
        }

        int port = Integer.parseInt(args[0]);

        try {
            final HttpServer server = new HttpServer(port);

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    server.shutdown();
                }
            });
            server.listen();
        } catch (ServerInitException e) {
            log.log(Level.SEVERE, "Unable to initialize the server: {0}", e.getMessage());
        }
    }
}
