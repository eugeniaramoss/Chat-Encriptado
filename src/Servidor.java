import javax.crypto.*;
import java.io.*;
import java.net.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class Servidor {
    private static final int MESSAGE_PORT = 999;
    private static final int FILE_PORT = 1000;
    private static Map<String, SocketAddress> clientes;
    private static Map<SocketAddress, SecretKey> clave;
    private static DatagramSocket serverMessages;
    private static DatagramSocket serverFiles;
    private static ReentrantLock lock;

    private static ArrayList<String> nombres = new ArrayList<>();
    private static int contador = 0;

    public static void main(String[] args) {
        clave = new HashMap<>();
        lock = new ReentrantLock();

        clientes = new HashMap<>();
        nombres.addAll(Arrays.asList("Andrea", "Eugenia", "Pablo", "Mario", "David", "Samu", "Bruno", "Victor", "Laura", "Diana"));

        startMessageThread();
        startFileThread();
    }

    private static void startMessageThread() {
        Thread messageThread = new Thread(() -> {
            try {
                serverMessages = new DatagramSocket(MESSAGE_PORT);
            } catch (SocketException e) {
                throw new RuntimeException(e);
            }

            while (true) {
                String userName;
                byte[] mensaje = new byte[1024];
                DatagramPacket paquete = new DatagramPacket(mensaje, mensaje.length);

                try {
                    serverMessages.receive(paquete);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                lock.lock();

                String paqueteString = new String(paquete.getData(), 0, paquete.getLength());

                if (new String(paquete.getData(), 0, paquete.getLength()).equals("exit")) {
                    desconectarCliente(paquete.getSocketAddress());
                } else {
                    SocketAddress direccionCliente = paquete.getSocketAddress();
                    if (clienteExiste(direccionCliente)) {
                        userName = userName(direccionCliente);
                        clientes.forEach((cliente, socket) -> {
                            String encriptado = encryptMensaje(paqueteString, socket);

                            try {
                                if (!userName.equals(cliente)) {
                                    reenviarMensajes(encriptado, userName, socket);
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } else {
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(paquete.getData());
                        ObjectInputStream objectInputStream;

                        try {
                            objectInputStream = new ObjectInputStream(inputStream);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        SecretKey key;

                        try {
                            key = (SecretKey) objectInputStream.readObject();
                        } catch (IOException | ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }

                        clave.put(direccionCliente, key);
                        userName = pickName();
                        clientes.put(userName, direccionCliente);
                    }

                    System.out.println("Recibido de: " + userName);
                    lock.unlock();
                }
            }
        });
        messageThread.start();
    }

    private static void startFileThread() {
        Thread fileThread = new Thread(() -> {
            try {
                serverFiles = new DatagramSocket(FILE_PORT);
            } catch (SocketException e) {
                throw new RuntimeException(e);
            }

            while (true) {
                String userName;
                byte[] mensaje = new byte[1024];
                DatagramPacket paquete = new DatagramPacket(mensaje, mensaje.length);

                try {
                    serverFiles.receive(paquete);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                lock.lock();
                SocketAddress direccionCliente = paquete.getSocketAddress();

                ByteArrayInputStream inputStream = new ByteArrayInputStream(paquete.getData());
                ObjectInputStream objectInputStream;

                try {
                    objectInputStream = new ObjectInputStream(inputStream);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                try {
                    File archivoRecibido = (File) objectInputStream.readObject();
                    userName = userName(direccionCliente);
                    clientes.forEach((cliente, socket) -> {

                        if (!userName.equals(cliente)) {
                            try {
                                reenviarFiles(archivoRecibido, userName, socket);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });

                } catch (IOException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }

                lock.unlock();
            }
        });
        fileThread.start();
    }

    private static String encryptMensaje(String paqueteString, SocketAddress socket) {

        byte[] encriptado;
        SecretKey key = clave.get(socket);

        try {
            Cipher cifrado = Cipher.getInstance("AES");
            cifrado.init(Cipher.ENCRYPT_MODE, key);
            encriptado = cifrado.doFinal(paqueteString.getBytes());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException |
                 InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        return Base64.getEncoder().encodeToString(encriptado);
    }

    private static String userName(SocketAddress direccionCliente) {
        AtomicReference<String> userName = new AtomicReference<>("user");
        clientes.forEach((key, value) -> {
            if (direccionCliente.equals(value)) {
                userName.set(key);
            }
        });
        return userName.get();
    }

    private static String pickName() {
        String name = "Usuario";
        if (!nombres.isEmpty()) {
            name = nombres.get(contador);
            nombres.remove(contador);
        }
        contador++;
        return name;
    }

    private static boolean clienteExiste(SocketAddress direccionCliente) {
        AtomicBoolean existe = new AtomicBoolean(false);

        clientes.forEach((key, value) -> {
            if (value.equals(direccionCliente)) {
                existe.set(true);
            }
        });
        System.out.println(existe.get());
        return existe.get();
    }

    private static void reenviarMensajes(String paqueteString, String nombreCliente, SocketAddress socket) throws IOException {
        byte[] mensajeBytes = paqueteString.getBytes();
        DatagramPacket paquete = new DatagramPacket(mensajeBytes, mensajeBytes.length, socket);
        serverMessages.send(paquete);
    }

    private static void reenviarFiles(File archivoRecibido, String userName, SocketAddress socket) throws IOException {
        FileInputStream stream = new FileInputStream(archivoRecibido);
        int c;
        String cadena = "Archivo: " + archivoRecibido.getAbsolutePath();
        while ((c = stream.read()) != -1) {
            if (cadena.length() < 100) {
                cadena += (char) c;
            } else {
                byte[] mensajeBytes = encryptMensaje(cadena, socket).getBytes();
                DatagramPacket paquete = new DatagramPacket(mensajeBytes, mensajeBytes.length, socket);
                serverFiles.send(paquete);
                cadena = "";
            }
        }
    }

    public static void desconectarCliente(SocketAddress socketAddress) {
        String nombreRemitente = userName(socketAddress);
        String mensaje = nombreRemitente + " se ha desconectado.";
        clientes.remove(nombreRemitente);

        clientes.forEach((key, value) -> {
            String encriptado = encryptMensaje(mensaje, value);
            try {
                reenviarMensajes(encriptado, key, value);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}