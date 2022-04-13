package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    static final String PUBLIC_FOLDER = "public";
    static final int THREAD_POOL_THREADS = 64;
    static final List<String> validPaths = initServerPaths();

    public void listen(short port) {
        final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_THREADS);
        try (final var serverSocket = new ServerSocket(port)) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    /* Закрытие сокета выносим отсюда в тред-обработчик из тредпула. Иначе запущенный тред
                    в тредпуле даже не успеет начать обработку, а socket уже будет закрыт в этом треде
                    * Remove Socket closure from here to the threadPool thread, otherwise threadPool thread
                    * will get closed socket
                    */
                    final var socket = serverSocket.accept();
                    threadPool.execute(() -> processClientRequests(socket));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    /**
     * Reads server resource paths from file system folder (constant)
     *
     * @return pathsList
     */
    private static List<String> initServerPaths() {
        List<String> validPaths = Arrays.asList((Objects.requireNonNull(new File(Server.PUBLIC_FOLDER).list())));
        validPaths.replaceAll(s -> "/" + s);
        return validPaths;
    }

    /**
     * Processes client request and closes the socket
     *
     * @param socket - accepted client socket
     */
    public static void processClientRequests(Socket socket) {
        try (final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             final var out = new BufferedOutputStream(socket.getOutputStream())) {

            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            final var requestLine = in.readLine();
            final var parts = requestLine.split(" ");

            if (parts.length != 3) {
                // just close socket
                socket.close();
                return;
            }

            final var path = parts[1];
            if (!validPaths.contains(path)) {
                out.write((
                        "HTTP/1.1 404 Not Found\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.flush();
                socket.close();
                return;
            }

            final var filePath = Path.of(".", PUBLIC_FOLDER, path);
            final var mimeType = Files.probeContentType(filePath);

            // special case for classic
            if (path.equals("/classic.html")) {
                final var template = Files.readString(filePath);
                final var content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();
                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.write(content);
                out.flush();
                socket.close();
                return;
            }

            final var length = Files.size(filePath);
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, out);
            out.flush();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}