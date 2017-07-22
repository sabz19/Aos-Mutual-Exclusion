package aosme;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;

import aosme.Kernel.Neighbor;


public class Kernel {
	
	
	Object connection_lock; // lock on connect before allowing client channels to send messages
	boolean has_token = false;
	int port,num_nodes,node_id,parent = -1;
	
	String file = "/home/012/s/sx/sxn164530/aosme/"+node_id+".txt";
	String token_content,time_stamp;
	
	BufferedReader input_reader;
	BufferedWriter output_writer;
	
	Selector channel_selector;
	
	HashSet<Neighbor> neighbors;
	HashMap<Integer,Neighbor> neighbor_Map;
	Queue<Integer> request_queue;
	
	
	Logger logger,critical_section_logger;
	FileHandler regular_file,critical_section_file;
	
	
	/*
	 *  Constructor to initialize all related objects
	 */
	public Kernel() throws IOException{
		
		connection_lock = new Object();
		neighbors = new HashSet<>();
		request_queue = new LinkedList<>();
		
		input_reader = new BufferedReader(new FileReader(file));
		output_writer = new BufferedWriter(new FileWriter(file));
		
		if(node_id == 0)
			has_token = true;
		
		/*
		 * Start all loggers
		 */
		
		regular_file = new FileHandler("/home/012/s/sx/sxn164530/aosme/Logs/logs"+node_id+".log");
		critical_section_file = new FileHandler("/home/012/s/sx/sxn164530/aosme/Logs/Critical/logs"+node_id+".log");
		
		logger = Logger.getLogger("Regular_Log");
		logger.setLevel(Level.INFO);
		logger.addHandler(regular_file);
		
		critical_section_logger = Logger.getLogger("Crit_Log");
		critical_section_logger.setLevel(Level.INFO);
		critical_section_logger.addHandler(critical_section_file);
		
	}
	
	/**
	 * Neighbor (inner)class to store all neighbor node information
	 */
	
	class Neighbor{
		
		
		SctpChannel client_channel;
		SctpServerChannel server_Channel;
		InetSocketAddress serverAddress;
		int node_id;
		
		public Neighbor(int node_id,String host_name,int port) throws UnknownHostException{
			
			this.node_id = node_id;
			serverAddress = new InetSocketAddress(InetAddress.getByName(host_name),port);
			
		}
	}
	
	public void add_Neighbors(int node_id,String host_name,int port) throws UnknownHostException{
		
		Neighbor neighbor = new Neighbor(node_id,host_name,port);
		neighbors.add(neighbor);
	}
	
	/*
	 * Connect to all open neighbor servers
	 */
	
	public void open_all_connections() throws InterruptedException{
		
		for(Neighbor neighbor:neighbors){
			boolean connection_established = false;
			while(!connection_established){
				try {
					
					neighbor.client_channel = SctpChannel.open();
					neighbor.client_channel.connect(neighbor.serverAddress);
					connection_established = true;
				} catch (IOException e) {
					Thread.sleep(2000);
				 }
			}
		}
		synchronized(connection_lock){
			connection_lock.notify();
		}
	}
	
	public void start_server() throws IOException{
		
			
		SctpServerChannel server = SctpServerChannel.open();
		InetSocketAddress serverAddress = new InetSocketAddress(port);
		server.bind(serverAddress);
		int i = 0;
		while(i < neighbors.size()){	
			
			SctpChannel serverChannel = server.accept();
			serverChannel.configureBlocking(false);
			serverChannel.register(channel_selector, SelectionKey.OP_READ);
			logger.info("Connection established");
			i++;
		}	
		
	}
	public void csEnter(){
		
		critical_section_logger.info(time_stamp);
		
		
	}
	public void csExit(){
		
	}
	public boolean check_for_application_request() throws IOException, InterruptedException{
		
		boolean flag = false;
		while(!flag){
			try{
				if(input_reader.readLine() != null){
					flag = true;
					if( (has_token) && (request_queue.isEmpty())){
						csEnter();
					}
					else if (!has_token && request_queue.isEmpty()){
						request_queue.add(node_id);
						send_messages("Request");
					}
					else
						request_queue.add(node_id);
						
				}
			}catch(Exception e){
				Thread.sleep(0);
			}
		}
		return true;
	}
	public void send_first_request() throws InterruptedException, IOException {
		
		synchronized (connection_lock){
			connection_lock.wait();
			
			if(has_token == false){
				check_for_application_request();
				send_messages("Request");
				IncomingChannelReader reader = new IncomingChannelReader();
				reader.receiveMessages();
			}
		}
	}
	
	public String bytes_to_string(ByteBuffer buffer){
		
		String string = new String(buffer.array());
		return string;
	}
	
	public void send_messages(String message){
		
		ByteBuffer sbuf = ByteBuffer.allocate(60);
		logger.info("Sending"+message+"message to"+" "+parent);
		logger.info("message is " + message);
		sbuf.put(message.getBytes());
		sbuf.flip();
		MessageInfo messageInfo = MessageInfo.createOutgoing(null,0);	
		try {
			neighbor_Map.get(parent).client_channel.send(sbuf, messageInfo);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	public static void main(String args[]) throws InterruptedException, IOException{
		
		String config_PATH = args[4];
		
		Kernel kernel = new Kernel();
		kernel.node_id = Integer.parseInt(args[0]);
		//args[1] is host name
		kernel.port = Integer.parseInt(args[2]);
		kernel.parent = Integer.parseInt(args[3]);
		
		Parser.startsWithToken(config_PATH, kernel.node_id);
		Parser.parseChildCount(config_PATH, kernel.node_id);
		
		/**
		 * Thread to run the server first and then call a method open connections to 
		 * connect all the client channels of neighbors to their respective server channel
		 */
		Thread connection_thread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					kernel.start_server();
				} catch (IOException e) {
					e.printStackTrace();
				}
				try {
					kernel.open_all_connections();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			}
		});
		connection_thread.start();  // Start the thread to call the above run function from the anonymous class

		kernel.send_first_request(); // Initiate the first request message	
	}

}
