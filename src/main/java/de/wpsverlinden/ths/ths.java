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

import java.io.IOException;

public class ths {

    public static void main(String[] args) {

        System.out.println("ths 1.0 - Tiny HTTP Server\nwritten by Oliver Verlinden (http://wps-verlinden.de)\n");
        // parse arguments
        if (args.length != 1) {
            System.out.println("Usage: ths <port>");
            System.exit(-1);
        }

        int port = Integer.parseInt(args[0]);

        try {
            final HttpServer server = new HttpServer(port);

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        server.shutdown();
                        Thread.sleep(2000);
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            server.listen();
        } catch (ServerInitException e) {
            System.err.println("Unable to initialize the server: " + e.getMessage());
        }
    }
}