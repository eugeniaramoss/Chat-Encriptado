import javax.crypto.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Scanner;

public class Cliente {
    private static SecretKey key;
    private static DatagramSocket socket;

    public static void main(String[] args) {
        try {
            socket = new DatagramSocket();
            key = generarKey();
            mandarKey(key);

            startSendThread();
            startReceiveThread();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static void startSendThread() {
        Thread sendThread = new Thread(() -> {
            while (true) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
                String mensaje;

                try {
                    mensaje = bufferedReader.readLine();
                    if (mensaje.equals("archivo")) {
                        mandarFile(elegirArchivo());
                    } else if (mensaje.equals("exit")) {
                        byte[] bytes = mensaje.getBytes();
                        DatagramPacket paquete = new DatagramPacket(bytes, bytes.length, InetAddress.getLocalHost(), 999);
                        socket.send(paquete);
                        System.out.println("Desconectado");
                        System.exit(0);
                    } else {
                        byte[] bytes = mensaje.getBytes();
                        DatagramPacket paquete = new DatagramPacket(bytes, bytes.length, InetAddress.getLocalHost(), 999);
                        socket.send(paquete);
                        System.out.println("Mensaje enviado");

                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        sendThread.start();
    }

    private static void startReceiveThread() {
        Thread receiveThread = new Thread(() -> {
            while (true) {
                byte[] recibido = new byte[1024];
                DatagramPacket paqueteRecibido = new DatagramPacket(recibido, recibido.length);
                try {
                    socket.receive(paqueteRecibido);
                    String paqueteRecibidoString = new String(paqueteRecibido.getData(), 0, paqueteRecibido.getLength());
                    String desencriptado = desencriptar(paqueteRecibidoString, key);
                    if (desencriptado.contains("{")) {
                        System.out.print(desencriptado);
                    } else {
                        System.out.println("Cliente - " + paqueteRecibido.getSocketAddress());
                        System.out.println("Fecha - " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME));
                        System.out.println(desencriptado);
                    }
                } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException |
                         IllegalBlockSizeException | BadPaddingException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        receiveThread.start();
    }

    private static File elegirArchivo() throws IOException {
        Scanner lector = new Scanner(System.in);
        int opcion;
        String ruta = "";

        System.out.println("¿Qué archivo quieres enviar?");
        System.out.println("1.- Contrato prácticas");
        System.out.println("2.- Curriculum");
        System.out.println("3.- Lista de empresas");
        System.out.println("4.- Numero seguridad social");
        opcion = lector.nextInt();
        lector.nextLine();

        ruta = switch (opcion) {
            case 1 ->
                    "C:\\Users\\Tester\\OneDrive\\Escritorio\\IMF\\2DAM\\PSP\\PracticaChatEncriptacion\\src\\File\\Contrato.tx";
            case 2 ->
                    "C:\\Users\\Tester\\OneDrive\\Escritorio\\IMF\\2DAM\\PSP\\PracticaChatEncriptacion\\src\\File\\CV.txt";
            case 3 ->
                    "C:\\Users\\Tester\\OneDrive\\Escritorio\\IMF\\2DAM\\PSP\\PracticaChatEncriptacion\\src\\File\\Empresas.txt";
            case 4 ->
                    "C:\\Users\\Tester\\OneDrive\\Escritorio\\IMF\\2DAM\\PSP\\PracticaChatEncriptacion\\src\\File\\NumSeguridadSocial.txt";
            default -> ruta;
        };
        return new File(ruta);
    }

    private static SecretKey generarKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);

        return keyGenerator.generateKey();
    }

    private static String desencriptar(String datoEncriptado, SecretKey secretKey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cifrado = Cipher.getInstance("AES");
        cifrado.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] bytesDesencriptados = cifrado.doFinal(Base64.getDecoder().decode(datoEncriptado));

        return new String(bytesDesencriptados);
    }

    private static void mandarFile(File file) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(byteOut);
        outputStream.writeObject(file);
        byte[] bytesFile = byteOut.toByteArray();
        DatagramPacket filePacket = new DatagramPacket(bytesFile, bytesFile.length, InetAddress.getLocalHost(), 1000);
        socket.send(filePacket);
    }

    private static void mandarKey(SecretKey key) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(byteOut);
        outputStream.writeObject(key);
        byte[] bytes = byteOut.toByteArray();
        DatagramPacket data = new DatagramPacket(bytes, bytes.length, InetAddress.getLocalHost(), 999);
        socket.send(data);
    }
}