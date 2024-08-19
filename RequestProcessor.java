package full;

import java.io.*;
import java.net.Socket;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;

public class RequestProcessor implements Runnable {
    private final static Logger logger = Logger.getLogger(RequestProcessor.class.getCanonicalName());
    private final Map<String, File> virtualHosts;
    private final String indexFileName;
    private final Socket socket;

    public RequestProcessor(Map<String, File> virtualHosts, String indexFileName, Socket socket) {
        this.virtualHosts = virtualHosts;
        this.indexFileName = indexFileName;
        this.socket = socket;
    }

    @Override
    public void run() {
        try (OutputStream rawOut = socket.getOutputStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStreamWriter writer = new OutputStreamWriter(rawOut);

            String host = null;
            String line;
            
            String requestLine = reader.readLine();
            logger.info(socket.getRemoteSocketAddress() + " " + requestLine);
            
//            StringBuilder requestLine = new StringBuilder();
//            while (true) {
//                int c = reader.read();
//                if (c == '\r' || c == '\n') break;
//                requestLine.append((char) c);
//            }
            String[] tokens = requestLine.split("\\s+");
            String method = tokens[0];
            String version = tokens.length > 2 ? tokens[2] : "";
            
            while (!(line = reader.readLine()).isEmpty()) {
                if (line.startsWith("Host:")) {
                    host = line.split(" ")[1].trim();
                    if (host.contains(":")) {
                        host = host.split(":")[0];
                    }
                    break;
                }
            }

            File rootDirectory = virtualHosts.get(host);
            
            if (rootDirectory == null) {
                rootDirectory = virtualHosts.get("default");
            }
            
            if (rootDirectory == null) {
                // Default root directory is not set, handle error
                String body = "<HTML><HEAD><TITLE>Server Error</TITLE></HEAD><BODY><H1>HTTP Error 500: Internal Server Error</H1></BODY></HTML>";
                writer.write("HTTP/1.1 500 Internal Server Error\r\nContent-Type: text/html\r\nContent-Length: " + body.length() + "\r\n\r\n");
                writer.write(body);
                writer.flush();
                return;
            }
            
            if (method.equals("GET")) {
                String fileName = tokens[1];
                if (fileName.endsWith("/")) fileName += indexFileName;
                String contentType = URLConnection.getFileNameMap().getContentTypeFor(fileName);
                if (tokens.length > 2) {
                    version = tokens[2];
                }
                
                File theFile = new File(rootDirectory, fileName.substring(1));
                
                if (theFile.canRead() && theFile.getCanonicalPath().startsWith(rootDirectory.getCanonicalPath())) {
                	byte[] theData = Files.readAllBytes(theFile.toPath());
                    if (version.startsWith("HTTP/")) { // send a MIME header
                        sendHeader(writer, "HTTP/1.1 200 OK", contentType, theData.length);
                    }
                    rawOut.write(theData);
                    rawOut.flush();
                } else {
                    // File not found
                    String body = "<HTML><HEAD><TITLE>File Not Found</TITLE></HEAD><BODY><H1>HTTP Error 404: File Not Found</H1></BODY></HTML>";
                    if (version.startsWith("HTTP/")) {
                        sendHeader(writer, "HTTP/1.1 404 File Not Found", "text/html; charset=utf-8", body.length());
                    }
                    writer.write(body);
                    writer.flush();
                }
            } else {
                // Method not supported
                String body = "<HTML><HEAD><TITLE>Not Implemented</TITLE></HEAD><BODY><H1>HTTP Error 501: Not Implemented</H1></BODY></HTML>";
                if (version.startsWith("HTTP/")) {
                    sendHeader(writer, "HTTP/1.1 501 Not Implemented", "text/html; charset=utf-8", body.length());
                }
                writer.write(body);
                writer.flush();
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error talking to " + socket.getRemoteSocketAddress(), ex);
        } finally {
            try {
                socket.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error closing socket", ex);
            }
        }
    }

    private void sendHeader(Writer out, String responseCode, String contentType, int length) throws IOException {
        out.write(responseCode + "\r\n");
        Date now = new Date();
        out.write("Date: " + now + "\r\n");
        out.write("Server: JHTTP 2.0\r\n");
        out.write("Content-length: " + length + "\r\n");
        out.write("Content-type: " + contentType + "\r\n\r\n");
//        out.write("Connection: close\r\n");
        out.flush();
    }

    public static void printVirtualHosts(Map<String, File> virtualHosts) {
        if (virtualHosts == null || virtualHosts.isEmpty()) {
            logger.info("The virtualHosts map is empty.");
            return;
        }

        logger.info("Virtual Hosts Configuration:");
        for (Map.Entry<String, File> entry : virtualHosts.entrySet()) {
            String host = entry.getKey();
            File directory = entry.getValue();
            String path = (directory != null) ? directory.getAbsolutePath() : "null";

            logger.info("Host: " + host + " -> Directory: " + path);
        }
    }
}
