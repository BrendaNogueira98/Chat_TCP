import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;


public class Comandos{
    static private String mensagem;
    
    public static void nick(Set<SelectionKey> allkeys,HashMap<String,Socket> NameKey,String nickname,SelectionKey key_principal,HashMap<Socket,String[]> clientsKey)  throws Exception{
        nickname=nickname.substring(0,nickname.length()-1);
        if(NameKey.containsKey(nickname)){
            mensagem = "ERRO";
            ChatServer.broadcast(allkeys,key_principal,null,false,mensagem);
        }else{
            Socket key_socket = ((SocketChannel)key_principal.channel()).socket();
            String nome_antigo = (String)key_principal.attachment();

            //verifica se já havia algum nome associado a chave e se sim, retira 
            if(nome_antigo!=null){
                NameKey.remove(nome_antigo);
            }
            //faz attach do nome novo na key
            key_principal.attach(nickname);

            //verificar se já existe na lista clientsKey
            if(clientsKey.containsKey(key_socket)){
                String [] estado_sala =clientsKey.get(key_socket);
                if(estado_sala[0]=="ESTADO_INIT"){
                    //atualiza estado se estado era INIT
                    estado_sala[0]="ESTADO_OUTSIDE";
                }   
            }
            //adiciona o nome na lista de nomes
            NameKey.put(nickname,key_socket);
            
            if(nome_antigo == null){
                mensagem = "NEWNICK " + nickname;
                ChatServer.broadcast(allkeys,key_principal, mensagem,true,"OK");
            }else{
                mensagem = "NEWNICK " + nome_antigo + " " + nickname;
                ChatServer.broadcast(allkeys,key_principal, mensagem,true,"OK");                
            }

        }
    }

    public static void join(Set<SelectionKey> allkeys,HashMap<String,HashSet<Socket>> salaClientes,String sala,SelectionKey key_principal,HashMap<Socket,String[]> clientsKey) throws Exception{
        Socket key_socket = ((SocketChannel)key_principal.channel()).socket();
        String [] estado_sala = clientsKey.get(key_socket);

        // verifica o estado do cliente
        if(estado_sala[0]=="ESTADO_OUTSIDE"){//atualiza estado se estado era OUTSIDE
            estado_sala[0]="ESTADO_INSIDE";
            estado_sala[1]=sala;
        }else if(estado_sala[0]=="ESTADO_INSIDE"){// se ja estiver em outra sala
            //faz left para outra sala e join para a nova sala
            leave(allkeys,salaClientes,key_principal,clientsKey,true);
            join(allkeys,salaClientes, sala,key_principal,clientsKey);
            return;
        }else{
            mensagem = "ERRO";
            ChatServer.broadcast(allkeys,key_principal,null,false,mensagem);
            return;
        }  

        String nome = (String)key_principal.attachment();
    
        if(salaClientes.containsKey(sala)){ // sala ja existe, entao iremos colocar o cliente nela
            HashSet<Socket> clientes=salaClientes.get(sala);
            clientes.add(key_socket);
        }else{ // cria uma sala nova
            HashSet<Socket> clientes = new HashSet<Socket>();
            clientes.add(key_socket);
            salaClientes.put(sala,clientes);
        }

        mensagem = "JOINED " + nome;
        ChatServer.broadcast(allkeys,key_principal,mensagem,true,"OK");
    }

    public static void leave(Set<SelectionKey> allkeys,HashMap<String,HashSet<Socket>> salaClientes,SelectionKey key_principal,HashMap<Socket,String[]> clientsKey,boolean leave_msg) throws Exception{
        Socket key_socket = ((SocketChannel)key_principal.channel()).socket();
        String [] estado_sala = clientsKey.get(key_socket);
        String sala;
        // verifica o estado do cliente
        if(estado_sala[0]=="ESTADO_INSIDE"){//atualiza estado se estado era INSIDE
            String nome = (String)key_principal.attachment();
            mensagem = "LEFT " + nome;
            if (!leave_msg) ChatServer.broadcast(allkeys,key_principal,mensagem,true,"BYE");
            else ChatServer.broadcast(allkeys,key_principal,mensagem,true,"OK");

            
            estado_sala[0]="ESTADO_OUTSIDE";
            sala = estado_sala[1]; // nome da sala em que o cliente estava
            estado_sala[1]="SEM_SALA";
        }else{
            // se ele nao estava em uma sala, da erro
            mensagem = "ERRO";
            if (leave_msg) ChatServer.broadcast(allkeys,key_principal,null,false,mensagem);
            return ; 
        }  

        

        //pega lista de clientes na sala
        HashSet<Socket> clientes = salaClientes.get(sala);


        // tira o cliente da lista de clientes na sala
        clientes.remove(key_socket);

    
    }

    public static void private_(HashMap<Socket,String[]> clientsKey,SelectionKey key_principal,HashMap<String,Socket> NameKey,String nickname,String message) throws Exception{
        //verifica se a pessoa tem um nick && o nome verificar se exite
        Socket socket_principal = ((SocketChannel)key_principal.channel()).socket();
        String [] estado_sala = clientsKey.get(socket_principal);

        if(estado_sala[0] == "ESTADO_INIT" || !NameKey.containsKey(nickname) ){
            mensagem = "ERRO";
            ChatServer.broadcast(null,key_principal,null,false,mensagem);
            return;
        }


        
        Socket socket_recebe = NameKey.get(nickname);
        String nome = (String)key_principal.attachment();

        mensagem = "PRIVATE " + nome + " " + message;

        ChatServer.private_comand(socket_principal,socket_recebe,mensagem);

       
        
    }


    public static void bye(Set<SelectionKey> all_keys,HashMap<String,Socket> NameKey,HashMap<String,HashSet<Socket>> salaClientes,SelectionKey key_principal,HashMap<Socket,String[]> clientsKey) throws Exception{
        
        // primeiro tira o cliente da sala que ele tiver
        leave(all_keys,salaClientes,key_principal,clientsKey,false);
        SocketChannel canal = (SocketChannel)key_principal.channel();
        Socket key_socket = (canal).socket();
        String nome = (String)key_principal.attachment();

        // remove dos nomes, caso tenha
        if (nome != null){
            NameKey.remove(nome);
        }
        // remove dos clientes
        clientsKey.remove(key_socket);



        // acho q aqui o melhor è mandar uma mensagem dizendo que è para fechar e nao fechar direto aqui
        HashSet<SelectionKey> copia=null;
        copia= new HashSet<SelectionKey>(all_keys);
        copia.remove(key_principal);

        try{
            ChatServer.broadcast(copia,key_principal,null,false,"BYE");
            key_principal.cancel();
            canal.close();
            System.out.println( "Closed " + canal );
        }catch( IOException ie ) {
            System.err.println( "Erro ao fechar ->    " + ie );
        };
        return ;
       
    }

}