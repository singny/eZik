package full;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by cybaek on 15. 5. 22..
 */
public class HttpServer {
    private static final Logger logger = Logger.getLogger(HttpServer.class.getCanonicalName());
    private static final int NUM_THREADS = 50;
    private static final String INDEX_FILE = "index.html";
    private final Map<String, ServerConfig.HostConfig> virtualHosts;
    private final int port;

    public HttpServer(ServerConfig config) {
        this.virtualHosts = config.getHosts(); // ServerConfig에서 가져온 hosts
        this.port = config.getPort();
    }
    public void start() throws IOException {
        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
        try (ServerSocket server = new ServerSocket(port)) {
            logger.info("Accepting connections on port " + server.getLocalPort());
            while (true) {
                try {
                    Socket request = server.accept();
                    Runnable r = new RequestProcessor(virtualHosts, INDEX_FILE, request);
                    pool.submit(r);
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Error accepting connection", ex);
                }
            }
        }
    }

    public static void main(String[] args) {
        Map<String, File> virtualHosts = new HashMap<>();
        
        // 호스트별 루트 디렉터리를 설정합니다.
//        virtualHosts.put("a.com", new File("src/main/resources/a_com_root"));
//        virtualHosts.put("b.com", new File("src/main/resources/b_com_root"));
//        virtualHosts.put("default", new File("src/main/resources/a_com_root"));
        
        // set the port to listen on
//        int port;
//        int port = 9090;
//        try {
//            port = Integer.parseInt(args[0]);
//            if (port < 0 || port > 65535) port = 80;
//        } catch (RuntimeException ex) {
//            port = 80;
//        }
     
        try {
            ServerConfig config = new ServerConfig("src/main/resources/config.json");
            HttpServer webserver = new HttpServer(config);
//            HttpServer webserver = new HttpServer(virtualHosts, port);
            webserver.start();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Server could not start", ex);
        }
    }
}
