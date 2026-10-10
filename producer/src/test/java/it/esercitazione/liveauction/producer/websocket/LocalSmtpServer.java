package it.esercitazione.liveauction.producer.websocket;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** SMTP in loopback per i test: nessun relay, autenticazione o destinatario esterno. */
final class LocalSmtpServer implements AutoCloseable {
    private final ServerSocket server;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean rejectNext = new AtomicBoolean();
    private final BlockingQueue<MimeMessage> messages = new LinkedBlockingQueue<>();
    private final BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();

    LocalSmtpServer() throws IOException {
        server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        workers.submit(() -> {
            while (!server.isClosed()) {
                try {
                    Socket socket = server.accept();
                    workers.submit(() -> serve(socket));
                } catch (IOException e) {
                    if (!server.isClosed()) errors.offer(e);
                }
            }
        });
    }

    int port() { return server.getLocalPort(); }
    void reset() { messages.clear(); errors.clear(); rejectNext.set(false); }
    void failNextDelivery() { rejectNext.set(true); }
    MimeMessage receive(long timeoutMs) throws InterruptedException { return messages.poll(timeoutMs, TimeUnit.MILLISECONDS); }
    void assertHealthy() {
        if (!errors.isEmpty()) throw new AssertionError("Errore server SMTP di test", errors.peek());
    }

    private void serve(Socket socket) {
        try (socket;
             var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            socket.setSoTimeout(10000);
            reply(writer, "220 localhost test SMTP");
            String line;
            while ((line = reader.readLine()) != null) {
                String command = line.split(" ", 2)[0].toUpperCase(java.util.Locale.ROOT);
                switch (command) {
                    case "EHLO", "HELO" -> reply(writer, "250 localhost");
                    case "MAIL", "RCPT", "RSET", "NOOP" -> reply(writer, "250 OK");
                    case "DATA" -> {
                        if (rejectNext.getAndSet(false)) {
                            reply(writer, "451 Temporary failure for retry test");
                            continue;
                        }
                        reply(writer, "354 End with dot");
                        var data = new StringBuilder();
                        while ((line = reader.readLine()) != null && !line.equals(".")) {
                            data.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                        }
                        if (line == null) throw new EOFException("DATA incompleto");
                        messages.offer(new MimeMessage(Session.getInstance(new Properties()),
                                new ByteArrayInputStream(data.toString().getBytes(StandardCharsets.UTF_8))));
                        reply(writer, "250 Accepted locally");
                    }
                    case "QUIT" -> { reply(writer, "221 Bye"); return; }
                    default -> reply(writer, "502 Not implemented");
                }
            }
        } catch (Exception e) {
            if (!server.isClosed()) errors.offer(e);
        }
    }

    private static void reply(BufferedWriter writer, String response) throws IOException {
        writer.write(response + "\r\n");
        writer.flush();
    }

    @Override public void close() throws IOException {
        server.close();
        workers.shutdownNow();
    }
}
