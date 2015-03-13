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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpServer {

    private static final Logger log = Logger.getLogger(HttpServer.class.getName());

    private final int port;
    private ServerSocket listenSocket;
    private volatile boolean shutdownRequested = false;

    public HttpServer(int port) throws ServerInitException {
        this.port = port;
        initializeServer();
    }

    private void initializeServer() throws ServerInitException {
        log.log(Level.INFO, "Initializing server on port {0}", port);
        try {
            listenSocket = new ServerSocket(port);
        } catch (IllegalArgumentException | IOException e) {
            throw new ServerInitException(e.getMessage());
        }
    }

    public void listen() {
        ExecutorService executor = Executors.newCachedThreadPool();
        Socket client = null;
        while (!shutdownRequested) {
            try {
                client = listenSocket.accept();
                executor.submit(new RequestProcessor(client));
            } catch (IOException e) {
                log.log(Level.SEVERE, e.getMessage());
            }
        }
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            log.log(Level.SEVERE, ex.getMessage());
        }
    }

    public void shutdown() {
        log.log(Level.INFO, "Server shutdown requested...");
        try {
            listenSocket.close(); //Causes listenSocket.accept() throw a SocketException
        } catch (IOException ex) {
            log.log(Level.SEVERE, ex.getMessage());
        }
        shutdownRequested = true;
    }
}
