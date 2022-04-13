import server.Server;

public class Main {
    static final short SERVER_PORT = 9999;

    public static void main(String[] args) {
        final var server = new Server();
        server.listen(SERVER_PORT);
    }
}