package vn.fpt.employee;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.xml.ws.Endpoint;
import vn.fpt.employee.repository.EmployeeRepository;
import vn.fpt.employee.service.EmployeeService;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Hosts the SOAP endpoint and its browser client on the same HTTP origin. */
public final class Application implements AutoCloseable {
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "/", "text/html; charset=utf-8", "/index.html", "text/html; charset=utf-8",
            "/app.js", "text/javascript; charset=utf-8", "/style.css", "text/css; charset=utf-8");
    private final HttpServer server;
    private final Endpoint endpoint;
    private final ExecutorService executor;

    private Application(HttpServer server, Endpoint endpoint, ExecutorService executor) {
        this.server = server;
        this.endpoint = endpoint;
        this.executor = executor;
    }

    public static Application start(String host, int port, String jdbcUrl) throws Exception {
        EmployeeRepository repository = new EmployeeRepository(jdbcUrl);
        repository.initialize();
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        Endpoint endpoint = Endpoint.create(new EmployeeService(repository));
        try {
            server.setExecutor(executor);
            server.createContext("/", Application::serveStatic);
            endpoint.publish(server.createContext("/services/employees"));
            server.start();
            return new Application(server, endpoint, executor);
        } catch (Exception exception) {
            endpoint.stop();
            server.stop(0);
            executor.shutdownNow();
            throw exception;
        }
    }

    public int getPort() { return server.getAddress().getPort(); }

    private static void serveStatic(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String contentType = CONTENT_TYPES.get(path);
            if (contentType == null) {
                sendText(exchange, 404, "Not found");
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendText(exchange, 405, "Method not allowed");
                return;
            }
            String resource = "/web" + ("/".equals(path) ? "/index.html" : path);
            try (InputStream input = Application.class.getResourceAsStream(resource)) {
                if (input == null) { sendText(exchange, 404, "Not found"); return; }
                byte[] bytes = input.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; style-src 'self'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        }
    }

    private static void sendText(HttpExchange exchange, int status, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        endpoint.stop();
        server.stop(0);
        executor.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        String host = System.getProperty("app.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("app.port", "8080"));
        Path dataDirectory = Path.of(System.getProperty("app.dataDir", "data")).toAbsolutePath();
        Files.createDirectories(dataDirectory);
        String url = "jdbc:h2:file:" + dataDirectory.resolve("employees").toString().replace('\\', '/');
        Application app = start(host, port, url);
        Runtime.getRuntime().addShutdownHook(new Thread(app::close));
        System.out.println("Web application: http://" + host + ":" + app.getPort() + "/");
        System.out.println("SOAP WSDL:       http://" + host + ":" + app.getPort() + "/services/employees?wsdl");
        System.out.println("Database:        " + dataDirectory.resolve("employees.mv.db"));
    }
}
