package full;

import java.io.*;
import java.net.Socket;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestProcessor implements Runnable {
    private static final Logger logger = Logger.getLogger(RequestProcessor.class.getCanonicalName());
    private final Map<String, ServerConfig.HostConfig> virtualHosts;
    private final String indexFileName;
    private final Socket socket;

    public RequestProcessor(Map<String, ServerConfig.HostConfig> virtualHosts, String indexFileName, Socket socket) {
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
//            logger.info(socket.getRemoteSocketAddress() + " " + requestLine);

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

            ServerConfig.HostConfig hostConfig = virtualHosts.getOrDefault(host, virtualHosts.get("default"));

            if (hostConfig == null || hostConfig.rootDirectory == null) {
                sendErrorPage(writer, rawOut, 500, version, hostConfig);
                return;
            }

            File rootDirectory = new File(hostConfig.rootDirectory);

            if (method.equals("GET")) {
                String fileName = tokens[1];
                if (fileName.endsWith("/")) fileName += indexFileName;
                String contentType = URLConnection.getFileNameMap().getContentTypeFor(fileName);

                File theFile = new File(rootDirectory, fileName.substring(1));

                if (theFile.canRead() && theFile.getCanonicalPath().startsWith(rootDirectory.getCanonicalPath())) {
                    byte[] theData = Files.readAllBytes(theFile.toPath());
                    if (version.startsWith("HTTP/")) {
                        sendHeader(writer, "HTTP/1.1 200 OK", contentType, theData.length);
                    }
                    rawOut.write(theData);
                    rawOut.flush();
                } else {
                    sendErrorPage(writer, rawOut, 404, version, hostConfig);
                }
            } else {
                sendErrorPage(writer, rawOut, 501, version, hostConfig);
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
        out.flush();
    }

    private void sendErrorPage(Writer writer, OutputStream rawOut, int errorCode, String version, ServerConfig.HostConfig hostConfig) throws IOException {
        String errorPageFileName = hostConfig.errorPages.getOrDefault(errorCode, "500.html");
        File errorFile = new File(hostConfig.rootDirectory, errorPageFileName);

        if (errorFile.exists() && errorFile.canRead()) {
            byte[] errorData = Files.readAllBytes(errorFile.toPath());
            if (version.startsWith("HTTP/")) {
                sendHeader(writer, "HTTP/1.1 " + errorCode + " Error", "text/html", errorData.length);
            }
            rawOut.write(errorData);
            rawOut.flush();
        } else {
            String body = "<HTML><HEAD><TITLE>Error " + errorCode + "</TITLE></HEAD><BODY><H1>HTTP Error " + errorCode + "</H1></BODY></HTML>";
            if (version.startsWith("HTTP/")) {
                sendHeader(writer, "HTTP/1.1 " + errorCode + " Error", "text/html", body.length());
            }
            writer.write(body);
            writer.flush();
        }
    }
}
