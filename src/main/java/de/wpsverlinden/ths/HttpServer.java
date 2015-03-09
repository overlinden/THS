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
import java.net.ServerSocket;
import java.net.Socket;

public class HttpServer {

    private int port;
    private ServerSocket socket;
    private boolean shutdown = false;

    public HttpServer(int port) throws ServerInitException {
        this.port = port;
        initializeServer();
    }

    private void initializeServer() throws ServerInitException {
        try {
            socket = new ServerSocket(port);
        } catch (IOException e) {
            throw new ServerInitException(e.getMessage());
        }
        System.out.println("Initialisation complete, listening for incoming connectionson port " + port);
    }

    public void listen() {

        Socket client;
        while (!shutdown) {
            try {
                client = socket.accept();
                Thread requestProcessor = new Thread(new RequestProcessor(client));
                requestProcessor.start();
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    public void shutdown() throws IOException {
        System.out.print("Server shutdown requested...");
        socket.close();
        shutdown = true;
    }
}
