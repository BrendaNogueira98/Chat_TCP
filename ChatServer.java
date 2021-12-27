import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;



public class ChatServer {
    
    // Buffer que recebera os dados
    static private final ByteBuffer buffer = ByteBuffer.allocate( 999999 );

    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
    static private final CharsetDecoder decoder = charset.newDecoder();
    
    // Variaveis que serão usadas em todas/quase todas as funcções
    static private ServerSocket socket_do_server;
    static private Selector selector;

    //maps
    static private HashMap<Socket,String[]> clientsKey = new HashMap<Socket,String[]>();
    static private HashMap<String,Socket> NameKey = new HashMap<String,Socket>();
    static private HashMap<String,HashSet<Socket>> salaClientes = new HashMap<String,HashSet<Socket>>();

    static public void main( String args[] ) throws Exception {
        // Porta onde o servidor ficara esperando sinal
        int port = Integer.parseInt(args[0]);
        try{
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.configureBlocking( false );

            // Pega a socket do servidor e liga a porta em que estamos escutando
            socket_do_server = ssc.socket();
            InetSocketAddress isa = new InetSocketAddress( port );
            socket_do_server.bind( isa );
            
            //Um único processo/thread usa select() para atender pedidos de múltiplas sockets em simultâneo
            selector = Selector.open();

            // liga a socket do server com o selector, permitindo escutar conexões
            ssc.register( selector, SelectionKey.OP_ACCEPT ); // Não entendi esse OP, ver (*1)
            System.out.println( "Listening on port "+port );

            espera_conexao(selector);
        }catch( IOException ie ) {
            System.err.println( ie );
        }
    }

    static public void espera_conexao(Selector selector) throws Exception{
        while(true){
            // Verifica a existencia de alguma atividade(novas conexões ou dado de uma conexão exitente)
            int num = selector.select();
            if (num == 0) continue;

            // Seleciona as "keys" das sockets q tiveram alguma atividade
            Set<SelectionKey> keys_ativas = selector.selectedKeys();

            // Pega todas as keys, independente se teve atividade ou não
            Set<SelectionKey> allkeys = selector.keys();

            itera_sobre_ativas(keys_ativas, allkeys);

            // ja lidamos com todas as conexões que ativaram, logo podemos limpar o set
            keys_ativas.clear();
        }
    }

    static public void itera_sobre_ativas(Set<SelectionKey> keys_ativas, Set<SelectionKey> allkeys) throws Exception{
        //Cria um iterador sobre as "keys" ativas. 
        Iterator<SelectionKey> iterador_keys_ativas = keys_ativas.iterator();

        // vai percorrendo os elementos do set
        while (iterador_keys_ativas.hasNext()) {
            SelectionKey key_atual = iterador_keys_ativas.next();
            
            
            // Verifica Qual é o tipo de atividade que foi recebida
            if(key_atual.isAcceptable()){ // registra conexão nova
                processIsAcceptable();

            }else if(key_atual.isReadable()){
                processIsReadable(key_atual,allkeys);
            }
        }
    }

    static private void processIsAcceptable() throws Exception{
        Socket socket_atual = socket_do_server.accept();
        System.out.println( "Got connection from " + socket_atual );
        SocketChannel sc = socket_atual.getChannel(); 
        sc.configureBlocking( false );
        sc.register( selector, SelectionKey.OP_READ ); //(*1)

        String [] estado_sala = {"ESTADO_INIT","SEM_SALA"};
        clientsKey.put(socket_atual,estado_sala);

    }

    static private void processIsReadable(SelectionKey key_atual,Set<SelectionKey> allkeys) throws Exception{
        SocketChannel sc = null;
        try{
            sc = (SocketChannel)key_atual.channel();

            boolean sem_erro = processOutput(sc,allkeys,key_atual);

            // se ocorreu um erro, entao é preciso fechar a conexão
            if(!sem_erro){
                key_atual.cancel();
                Socket s = null;
                try {
                    s = sc.socket();
                    System.out.println( "Closing connection to "+s );
                    s.close();
                } catch( IOException ie ) {
                    System.err.println( "Error closing socket "+s+": "+ie );
                }
            }
        } catch( IOException ie ) {
            key_atual.cancel();
            try {
                sc.close();
            } catch( IOException ie2 ) { System.out.println( ie2 ); }

            System.out.println( "Closed " + sc );
        }
    }

    static private boolean processOutput(SocketChannel sc, Set<SelectionKey> allkeys, SelectionKey key_atual)  throws Exception{
        // Le mensagem e coloca no buffer
        
        boolean final_ = false;
        String message = "";
        do{            
            sc.read( buffer );
            buffer.flip();
            String a = decoder.decode(buffer).toString();
            message +=a;
            buffer.clear();
            buffer.rewind();
            if(message.isEmpty() && a.isEmpty()) break;
        }while(!message.contains("\n"));
       
        // Pelo oq eu entendi, chegou no limite do buffer, então não tem mais espaço (*2),
        if (buffer.limit()==0) {
            return false;
        }
        
        // envia a mensagem para todos os clientes, ou define um nickname para a key atual, q é a principal
        boolean resultado = itera_sobre_todo_mundo(allkeys,key_atual,message);
        return resultado;
    }

    static public boolean itera_sobre_todo_mundo(Set<SelectionKey> allkeys,SelectionKey key_principal,String message) throws Exception{
        analisa_mensagem(message,key_principal, allkeys);
        return true;
    }
    
    static public void analisa_mensagem(String mensagem,SelectionKey key_principal,Set<SelectionKey> all_keys) throws Exception{
        if(mensagem.startsWith("/") && !mensagem.startsWith("//")){
            analisa_comando(mensagem,key_principal,all_keys);
        }else{
            mensagem_simples(mensagem,key_principal,all_keys);
        }
    }

    static public void mensagem_simples(String mensagem,SelectionKey key_principal,Set<SelectionKey> all_keys) throws Exception{
        mensagem=mensagem.substring(0,mensagem.length()-1);
        Socket socket_principal = ((SocketChannel)key_principal.channel()).socket();
        String[] info = clientsKey.get(socket_principal);
        String estado = info[0];
        // remove o primeiro
        if(mensagem.startsWith("//")){
            mensagem = mensagem.substring(1);
        }

        String nome = (String)key_principal.attachment();
        //verifica se já havia algum nome associado a chave e se sim, retira 
        if(nome==null || estado != "ESTADO_INSIDE"){
            mensagem = "ERRO";
            ChatServer.broadcast(all_keys,key_principal, null,false,mensagem);     
            return;
        }

        String mensagemFinal ="MESSAGE "+  nome + " " + mensagem;
        ChatServer.broadcast(all_keys,key_principal, mensagemFinal,true,mensagemFinal);     


    }

    static public void analisa_comando(String mensagem,SelectionKey key_principal,Set<SelectionKey> all_keys)throws Exception{
        String[] arrOfStr = mensagem.split(" ", -2);
        if(arrOfStr[0].startsWith("/nick")){
            Comandos.nick(all_keys,NameKey,arrOfStr[1],key_principal,clientsKey);
        }else if(arrOfStr[0].startsWith("/join")){
            Comandos.join(all_keys,salaClientes,arrOfStr[1],key_principal,clientsKey);
        }else if(arrOfStr[0].startsWith("/leave")){
            Comandos.leave(all_keys,salaClientes,key_principal,clientsKey,true);
        }else if(arrOfStr[0].startsWith("/bye")){
            Comandos.bye(all_keys,NameKey,salaClientes,key_principal,clientsKey);
        }else if(arrOfStr[0].startsWith("/private")){
            arrOfStr = mensagem.split(" ", 3); // /private nome message
            Comandos.private_(clientsKey,key_principal,NameKey,arrOfStr[1],arrOfStr[2]);  
        }else{
            System.out.println("COMANDO NAO EXISTE = " + arrOfStr[0]);
        }
    }

    static public void  broadcast(Set<SelectionKey> allkeys,SelectionKey key_principal,String mensagem, boolean send_to_all,String send_to_user) throws Exception{
        mensagem+= "\n";
        send_to_user+="\n";
        CharBuffer cb = CharBuffer.wrap(mensagem.toCharArray());
        ByteBuffer bb = encoder.encode(cb); 
        
        if(send_to_all){ // neste caso temos que enviar para todo mundo a mensagem
            Socket socket_principal = ((SocketChannel)key_principal.channel()).socket();
            String[] info = clientsKey.get(socket_principal);
            String sala = info[1];
            if(sala != "SEM_SALA"){
                System.out.println("Enviando para todos da sala " + sala);

                HashSet<Socket> clientesNaSala = salaClientes.get(sala);
                Iterator<Socket> iterador_todos_clientes = clientesNaSala.iterator();
                while(iterador_todos_clientes.hasNext()){
                    Socket socket_atual= iterador_todos_clientes.next();
                    if(socket_principal == socket_atual)  continue;
                    SocketChannel sc = (SocketChannel)socket_atual.getChannel();
                    sc.write(bb);
                    bb.rewind();    
                }
            }   
        }
        // se chegou aqui, entao è so para enviar para o usuario q fez o codigo. Chega aqui quando nao tem sala ou quando è algum erro
        SocketChannel sc = (SocketChannel)key_principal.channel();
        cb = CharBuffer.wrap(send_to_user.toCharArray());
        bb = encoder.encode(cb); 
        sc.write(bb);
        
    }

    static public void private_comand(Socket socket_principal,Socket socket_recebe,String retorna) throws Exception{
        retorna+= "\n";
        CharBuffer cb = CharBuffer.wrap(retorna.toCharArray());
        ByteBuffer bb = encoder.encode(cb); 
        
        SocketChannel sp = (SocketChannel)socket_principal.getChannel();
        SocketChannel sr = (SocketChannel)socket_recebe.getChannel();
        sp.write(bb);
        bb.rewind();
        sr.write(bb);   

    }
}

/*
(*1) https://docs.oracle.com/javase/7/docs/api/java/nio/channels/SelectionKey.html#OP_ACCEPT
(*2) https://docs.oracle.com/javase/7/docs/api/java/nio/Buffer.html#limit()
(*3) https://docs.oracle.com/javase/7/docs/api/java/nio/channels/SelectionKey.html#attach%28java.lang.Object%29
*/

