import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    Socket clientSocket;
    DataOutputStream outToServer;
    BufferedReader inFromServer;

    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
       
        clientSocket = new Socket(server, port);




    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor
        if(message.startsWith("/")){
            String[] arrOfStr = message.split(" ", -2);
            if(arrOfStr[0].compareTo("/private") != 0 && arrOfStr[0].compareTo("/nick") != 0 && arrOfStr[0].compareTo("/join") != 0 && arrOfStr[0].compareTo("/leave") != 0 && arrOfStr[0].compareTo("/bye")!= 0){
                String m = "/" + message;
                message = m;
            }
        }
        outToServer = new DataOutputStream(clientSocket.getOutputStream());
        outToServer.writeBytes(message + "\n");
    }

    
    // Método principal do objecto
    public void run() throws IOException {
        // PREENCHER AQUI
        inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        while(true){
            String sentence = inFromServer.readLine();
            organiza(sentence);
            if(sentence.compareTo("BYE")==0){
                System.exit(0);
            }
        }

    }
    
    public void organiza(String mensagem){
        String[] arrOfStr = mensagem.split(" ", -2);
        switch(arrOfStr[0]){
            case "MESSAGE": 
                arrOfStr = mensagem.split(" ", 3);
                printMessage( arrOfStr[1] + ":" + arrOfStr[2] + "\n");
                break;
            case "NEWNICK":
                arrOfStr = mensagem.split(" ", 3);
                printMessage( arrOfStr[1] + " mudou de nome para " + arrOfStr[2] + "\n");
                break;
            case "JOINED":
                arrOfStr = mensagem.split(" ", 2);
                printMessage( arrOfStr[1] + " acaba de entrar na sala" + "\n");
                break;
            case "LEFT":
                arrOfStr = mensagem.split(" ", 2);
                printMessage( arrOfStr[1] + " saiu da sala"  + "\n");
                break;
            
            case "PRIVATE": //priv nome mensagem
                arrOfStr = mensagem.split(" ", 3);
                printMessage("[Privado] " + arrOfStr[1] + ": " + arrOfStr[2]  + "\n");
                break;
        }
        switch(mensagem){
            case "OK":
                printMessage(mensagem + "\n");
                System.out.println(mensagem);
                break;
            case "ERRO":
                printMessage(mensagem + "\n");
                System.out.println(mensagem);
                break;
            default:
                break;
        }
    }
    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}