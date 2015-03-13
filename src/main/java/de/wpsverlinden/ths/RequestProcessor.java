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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.activation.MimetypesFileTypeMap;

public class RequestProcessor implements Runnable {

    private static final Logger log = Logger.getLogger(RequestProcessor.class.getName());

    private final String SERVER_NAME = "ths - Tiny HTTP Server";
    private final MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
    private final Socket clientSocket;
    private String requestLine;
    private final InputStream inStream;
    private final OutputStream outStream;

    public RequestProcessor(Socket client) throws IOException {
        this.clientSocket = client;
        this.inStream = client.getInputStream();
        this.outStream = new BufferedOutputStream(client.getOutputStream());
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(inStream))) {

            if ((requestLine = in.readLine()) == null) {
                return;
            }
            processRequest();

        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        } finally {
            if (clientSocket != null) {
                try {
                    clientSocket.close();
                } catch (IOException ex) {
                    log.log(Level.SEVERE, ex.getMessage());
                }
            }
        }
    }

    private void processRequest() throws IOException {
        String requestPath = requestLine.substring(4, requestLine.length() - 9).trim();
        requestPath = URLDecoder.decode(requestPath, "utf-8");
        File file = new File("." + requestPath);
        log.log(Level.FINE, "Incoming request for {0}", requestPath);

        if (!validRequestFormat(requestLine)) {
            sendBadRequestResponse();
        } else if (!validRequestDst(requestPath)) {
            sendForbiddenResponse(requestPath);
        } else if (file.isDirectory() && !requestPath.endsWith("/")) {
            sendMovedPermanentlyResponse(requestPath);
        } else if (file.exists()) {
            if (file.isDirectory()) {
                boolean containsIndexFile = false;
                // try to deliver index.html or index.htm
                File[] indexfiles = {new File(requestPath + "index.html"), new File(requestPath + "index.htm")};

                for (File indexfile : indexfiles) {
                    if (indexfile.exists() && indexfile.isFile()) {
                        log.log(Level.FINER, " Located index file at {0}", file.getPath());
                        containsIndexFile = true;
                        file = indexfile;
                        break;
                    }
                }

                if (!containsIndexFile) {
                    log.log(Level.FINE, "Could not find index file in {0}. Generating directory index.", requestPath);
                    sendDirectoryIndexResponse(file);
                }
            } else {
                log.log(Level.FINE, "Sending file {0} ({1} bytes of data)", new Object[]{file.getPath(), file.length()});
                sendFile(file, outStream);
            }
        } else {
            log.log(Level.FINE, "Requested file {0} not found", requestPath);
            sendNotFoundResponse();
        }
    }

    private void sendNotFoundResponse() throws IOException {
        String response = generateErrorResponse(clientSocket, "404", "Not Found", "The requested URL was not found on this server.");
        String header = buildHeader(response, 404, "Not Found");
        outStream.write(header.getBytes());
        outStream.write(response.getBytes());
        outStream.flush();
    }

    private void sendDirectoryIndexResponse(File file) throws IOException {
        String response = generateDirIndex(file, clientSocket.getLocalAddress().getHostName(), clientSocket.getLocalPort());
        String header = buildHeader(response, 200, "OK");
        outStream.write(header.getBytes());
        outStream.write(response.getBytes());
        outStream.flush();
    }

    private void sendMovedPermanentlyResponse(String requestPath) throws IOException {
        log.log(Level.FINE, "Redirecting to {0}", requestPath);
        String header = "HTTP/1.0 301 Moved Permanently\r\n"
                + "Location: http://" + clientSocket.getLocalAddress().getHostAddress() + ":" + clientSocket.getLocalPort() + requestPath + "/\r\n"
                + "\r\n";
        outStream.write(header.getBytes());
        outStream.flush();
    }

    private void sendForbiddenResponse(String requestPath) throws IOException {
        log.log(Level.FINE, "Blocking request for {0}", requestPath);
        String response = generateErrorResponse(clientSocket, "403", "Forbidden",
                "You don't have permission to access the requested URL.");
        String header = buildHeader(response, 403, "Forbidden");
        outStream.write(header.getBytes());
        outStream.write(response.getBytes());
        outStream.flush();
    }

    private void sendBadRequestResponse() throws IOException {
        log.log(Level.FINE, "Invalid request string: {0}", requestLine);
        String response = generateErrorResponse(clientSocket, "400", "Bad Request",
                "Your browser sent a request that this server could not understand.");
        String header = buildHeader(response, 400, "Bad Request");
        outStream.write(header.getBytes());
        outStream.write(response.getBytes());
        outStream.flush();
    }

    private String buildHeader(String response, int code, String codeText) {
        String header = "HTTP/1.0 " + code + " " + codeText + "\r\n"
                + "Content-Type: text/html \r\n"
                + "Content-Length: " + response.length() + "\r\n"
                + "Connection: close\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: " + SERVER_NAME + "\r\n"
                + "\r\n";
        return header;
    }

    private String buildHeader(File file, int code, String codeText) {
        String header = "HTTP/1.0 " + code + " " + codeText + "\r\n"
                + "Content-Type: " + mimeTypesMap.getContentType(file) + "\r\n"
                + "Content-Length: " + file.length() + "\r\n"
                + "Connection: close\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: " + SERVER_NAME + "\r\n"
                + "\r\n";
        return header;
    }

    private String generateDirIndex(File dir, String hostname, int port) {
        String dirIndex = "<html><head><title>Index of " + dir.getPath() + "</title></head><body bgcolor=\"white\">"
                + "<h1>Index of " + dir.getPath() + "</h1><hr><pre><a href=\"../\">../</a>\n"
                + "<b>Type\tSize\t\tFilenme</b>\n";
        File[] files = dir.listFiles();
        DecimalFormat format = new DecimalFormat("#0.00");
        for (File file : files) {
            if (file.isFile()) {
                double filesize = file.length();
                String sizeString = "B";
                if (filesize > 1024) {
                    filesize /= 1024;
                    sizeString = "KB";
                }
                if (filesize > 1024) {
                    filesize /= 1024;
                    sizeString = "MB";
                }
                if (filesize > 1024) {
                    filesize /= 1024;
                    sizeString = "GB";
                }
                dirIndex += "[File]\t" + format.format(filesize) + "\t" + sizeString + "\t<a href=\"" + file.getName() + "\">" + file.getName() + "</a>\n";
            } else {
                dirIndex += "[Dir]\t --- \t\t<a href=\"" + file.getName() + "\">" + file.getName() + "</a>\n";
            }
        }
        dirIndex += "</pre><hr><ADDRESS>" + SERVER_NAME + " at " + hostname + " Port " + port + "</ADDRESS></body></html>";
        return dirIndex;
    }

    private void sendFile(File file, OutputStream outStream) throws FileNotFoundException, IOException {
        String header = buildHeader(file, 200, "OK");
        outStream.write(header.getBytes());
        outStream.flush();
        sendRawFile(file, outStream);
    }

    private boolean validRequestDst(String req) {
        return !req.contains("..") && !req.contains("/.ht") && !req.endsWith("~");
    }

    private boolean validRequestFormat(String request) {
        return (request.startsWith("GET") && request.length() >= 14 && (request.endsWith("HTTP/1.0") || request
                .endsWith("HTTP/1.1")));
    }

    private String generateErrorResponse(Socket connection, String code, String title, String msg) {
        return "<HTML><TITLE>" + code + " " + title + "</TITLE>\r\n"
                + "</HEAD><BODY>\r\n" + "<H1>" + title + "</H1>\r\n" + msg + "<P>\r\n"
                + "<HR><ADDRESS>" + SERVER_NAME + " at " + connection.getLocalAddress().getHostName() + " Port "
                + connection.getLocalPort() + "</ADDRESS>\r\n" + "</BODY></HTML>\r\n";
    }

    private void sendRawFile(File file, OutputStream out) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            while (fis.available() > 0) {
                out.write(buffer, 0, fis.read(buffer));
            }
            out.flush();
        } catch (IOException e) {
            System.err.println(e);
        }
    }
}
