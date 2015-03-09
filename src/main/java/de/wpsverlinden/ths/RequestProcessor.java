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
import java.util.Date;

import javax.activation.MimetypesFileTypeMap;

public class RequestProcessor implements Runnable {

    private final String SERVER_NAME = "ths - Tiny HTTP Server";
    private MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
    private Socket client;
    private String requestLine;
    private InputStream inStream;
    private OutputStream outStream;

    public RequestProcessor(Socket client) throws IOException {
        this.client = client;
        this.inStream = client.getInputStream();
        this.outStream = new BufferedOutputStream(client.getOutputStream());
    }

    @Override
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(inStream));

            if ((requestLine = in.readLine()) == null) {
                return;
            }

            processRequest();

            if (client != null) {
                client.close();
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

    }

    private void processRequest() throws IOException {
        String requestPath = requestLine.substring(4, requestLine.length() - 9).trim();
        String header;
        String response = "";

        requestPath = URLDecoder.decode(requestPath, "utf-8");
        System.out.println("[IN] Request for " + requestPath);

        if (!validRequestFormat(requestLine)) {
            System.out.println("[INFO] Invalid request string: " + requestLine);
            response = generateErrorResponse(client, "400", "Bad Request",
                    "Your browser sent a request that this server could not understand.");
            header = "HTTP/1.0 400 Bad Request\r\n"
                    + "Content-Type: text/html \r\n"
                    + "Content-Length: " + response.length() + "\r\n"
                    + "Connection: close\r\n"
                    + "Date: " + new Date() + "\r\n"
                    + "Server: " + SERVER_NAME + "\r\n"
                    + "\r\n";

            outStream.write(header.getBytes());
            outStream.write(response.getBytes());
            outStream.flush();
            return;
        }

        if (!validRequestDst(requestPath)) {
            System.out.println("[INFO] Forbidden request for " + requestPath);
            response = generateErrorResponse(client, "403", "Forbidden",
                    "You don't have permission to access the requested URL.");
            header = "HTTP/1.0 403 Forbidden\r\n"
                    + "Content-Type: text/html \r\n"
                    + "Content-Length: " + response.length() + "\r\n"
                    + "Connection: close\r\n"
                    + "Date: " + new Date() + "\r\n"
                    + "Server: " + SERVER_NAME + "\r\n"
                    + "\r\n";

            outStream.write(header.getBytes());
            outStream.write(response.getBytes());
            outStream.flush();
            return;
        }

        File file = new File("." + requestPath);
        if (file.isDirectory() && !requestPath.endsWith("/")) {
            System.out.println("[INFO] Redirecting to " + requestPath);
            header = "HTTP/1.0 301 Moved Permanently\r\n"
                    + "Location: http://" + client.getLocalAddress().getHostAddress() + ":" + client.getLocalPort() + requestPath + "/\r\n"
                    + "\r\n";

            outStream.write(header.getBytes());
            outStream.write(response.getBytes());
            outStream.flush();
            return;
        }

        if (file.isDirectory()) {
            boolean containsIndexFile = false;
            // try to deliver index.html or index.htm
            File[] indexfiles = {new File(requestPath + "index.html"), new File(requestPath + "index.htm")};

            for (int i = 0; i < indexfiles.length; i++) {
                if (indexfiles[i].exists() && indexfiles[i].isFile()) {
                    System.out.println("[INFO] Located index file " + file.getPath());
                    containsIndexFile = true;
                    file = indexfiles[i];
                    break;
                }
            }

            if (!containsIndexFile) {
                System.out.println("[INFO] Could not find index file in " + requestPath);
                System.out.println("[OUT] Generating directory index for " + requestPath);
                response = generateDirIndex(file, client.getLocalAddress().getHostName(), client.getLocalPort());
                header = "HTTP/1.0 200 OK\r\n"
                        + "Content-Type: text/html\r\n"
                        + "Content-Length: " + response.length() + "\r\n"
                        + "Connection: close\r\n"
                        + "Date: " + new Date() + "\r\n"
                        + "Server: " + SERVER_NAME + "\r\n"
                        + "\r\n";

                outStream.write(header.getBytes());
                outStream.write(response.getBytes());
                outStream.flush();
                return;
            }
        }

        if (file.exists() && file.isFile()) {
            System.out.println("[OUT] Sending file " + file.getPath() + " (" + file.length() + " bytes of data)");
            sendFile(file, outStream);
            outStream.flush();
        } else {
            System.out.println("[OUT] Requested file " + requestPath + " not found");
            response = generateErrorResponse(client, "404", "Not Found", "The requested URL was not found on this server.");
            header = "HTTP/1.0 404 Not Found\r\n"
                    + "Content-Type: text/html\r\n"
                    + "Content-Length: " + response.length() + "\r\n"
                    + "Connection: close\r\n"
                    + "Date: " + new Date() + "\r\n"
                    + "Server: " + SERVER_NAME + "\r\n"
                    + "\r\n";

            outStream.write(header.getBytes());
            outStream.write(response.getBytes());
            outStream.flush();
        }
    }

    private String generateDirIndex(File file, String hostname, int port) {
        String dirIndex = "<html><head><title>Index of " + file.getPath() + "</title></head><body bgcolor=\"white\">"
                + "<h1>Index of " + file.getPath() + "</h1><hr><pre><a href=\"../\">../</a>\n"
                + "<b>Type\tSize\t\tFilenme</b>\n";
        File[] files = file.listFiles();
        DecimalFormat f = new DecimalFormat("#0.00");
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                double filesize = files[i].length();
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
                if (files[i].isDirectory()) {
                    dirIndex += "[Dir]\t --- \t\t<a href=\"" + files[i].getName() + "\">" + files[i].getName() + "</a>\n";
                } else {
                    dirIndex += "[File]\t" + f.format(filesize) + "\t" + sizeString + "\t<a href=\"" + files[i].getName() + "\">" + files[i].getName() + "</a>\n";
                }
            }
        }
        dirIndex += "</pre><hr><ADDRESS>" + SERVER_NAME + " at " + hostname + " Port " + port + "</ADDRESS></body></html>";
        return dirIndex;
    }

    private void sendFile(File file, OutputStream outStream) throws FileNotFoundException, IOException {
        InputStream fis = new FileInputStream(file);
        String header = "HTTP/1.0 200 OK\r\n"
                + "Content-Type: " + mimeTypesMap.getContentType(file) + "\r\n"
                + "Content-Length: " + file.length() + "\r\n"
                + "Connection: close\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: " + SERVER_NAME + "\r\n"
                + "\r\n";
        outStream.write(header.getBytes());
        outStream.flush();
        sendRawFile(fis, outStream);
        fis.close();
    }

    private boolean validRequestDst(String req) {
        return req.indexOf("..") == -1 && req.indexOf("/.ht") == -1 && !req.endsWith("~");
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

    private void sendRawFile(InputStream file, OutputStream out) {
        try {
            byte[] buffer = new byte[1024];
            while (file.available() > 0) {
                out.write(buffer, 0, file.read(buffer));
            }
            out.flush();
        } catch (IOException e) {
            System.err.println(e);
        }

    }
}
