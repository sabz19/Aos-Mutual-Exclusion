package aosme;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;

import aosme.Kernel.Neighbor;


public class Kernel {
	
	
	Object connection_lock; // lock on connect before allowing client channels to send messages
	private int port,num_nodes,node_id,parent = -1;
	boolean greedy;
	boolean done;
	
	String file = System.getProperty("user.home") + "/aosme/"+node_id+".txt";
	String token_content,time_stamp;
	
	BufferedReader input_reader;
	BufferedWriter output_writer;
	
	Selector channel_selector;
	
	static HashSet<Neighbor> neighbors = new HashSet<>();
	static HashMap<Integer,Neighbor> neighbor_Map = new HashMap<>();
	Queue<Integer> request_queue; //Store all incoming requests in this queue
	
	
	Logger logger,critical_section_logger;
	FileHandler regular_file,critical_section_file;
	
	private Pipe.SourceChannel pipein;
	
	
	/*
	 *  Constructor to initialize all related objects
	 */
	public Kernel(int node_id,int port,int parent,int num_nodes) throws IOException{
		
		this.node_id = node_id;
		this.port = port;
		this.parent = parent;
		this.num_nodes = num_nodes;
		done = false;
		
		connection_lock = new Object();
		request_queue = new LinkedList<>();
		channel_selector = Selector.open();
		//input_reader = new BufferedReader(new FileReader(file));
		//output_writer = new BufferedWriter(new FileWriter(file));
		
		/*
		 * Start all loggers
		 */
		
		regular_file = new FileHandler(System.getProperty("user.home")+"/aosme/Logs/"+node_id+".log");
		critical_section_file = new FileHandler(System.getProperty("user.home")+"/aosme/Logs/Critical/"+node_id+".log");
		
		SimpleFormatter formatter = new SimpleFormatter();
		regular_file.setFormatter(formatter);
		critical_section_file.setFormatter(formatter);
		
		logger = Logger.getLogger("Regular_Log");
		logger.setLevel(Level.INFO);
		logger.addHandler(regular_file);
		
		critical_section_logger = Logger.getLogger("Crit_Log");
		critical_section_logger.setLevel(Level.INFO);
		critical_section_logger.addHandler(critical_section_file);
		
		logger.info("My node_id"+node_id);
		logger.info("My port"+port);
		logger.info("My parent"+parent);
		
	}
	/**
	 * Neighbor (inner)class to store all neighbor node information
	 */
	public int get_node_id(){
		return node_id;
	}
	public int get_num_nodes(){
		return num_nodes;
	}
	public HashSet get_Neighbors(){
		return neighbors;
	}
	
	public static class Neighbor{
		
		SctpChannel client_channel;
		SctpServerChannel server_Channel;
		InetSocketAddress serverAddress;
		int node_id;
		boolean done;
		
		public Neighbor(int node_id,String host_name,int port) throws UnknownHostException{
			
			this.node_id = node_id;
			serverAddress = new InetSocketAddress(InetAddress.getByName(host_name),port);
			done = false;
			
		}
		public static void add_Neighbors(int node_id,String host_name,int port) throws UnknownHostException{
			
			Neighbor neighbor = new Neighbor(node_id,host_name,port);
			neighbors.add(neighbor);
			neighbor_Map.put(node_id, neighbor);
		}
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
		logger.info("ALL connections done releasing lock");
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
	
	private boolean hasToken() {
	    if (parent == node_id)
	        return true;
	    else
	        return false;
	}
	
	// Function called within the kernel to grant permission to the application
	private void csGrant() throws IOException{
        String home = System.getProperty("user.home");
        Path k2a = Paths.get(home, "aosme", "Kern" + node_id + "ToApp" + node_id + ".cnl");
        OutputStream toApp = Files.newOutputStream(k2a, StandardOpenOption.APPEND,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		critical_section_logger.info("Granting request. Timestamp: " + time_stamp);
		toApp.write(MessageType.CSGRANT.toCode());
		toApp.flush();
		toApp.close();
	}
	
	// Interface to apps to enter the critical section. ID of the app is used
	// to determine what file (channel) to access the kernel with.
	public static void csEnter(int id) throws Exception {
        String home = System.getProperty("user.home");
        Path a2k = Paths.get(home, "aosme", "App" + id + "ToKern" + id + ".cnl");
        Path k2a = Paths.get(home, "aosme", "Kern" + id + "ToApp" + id + ".cnl");
        OutputStream toKern = Files.newOutputStream(a2k, StandardOpenOption.APPEND,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        InputStream fromKern = Files.newInputStream(k2a, StandardOpenOption.CREATE,
                StandardOpenOption.READ);
        toKern.write(MessageType.CSREQUEST.toCode());
        toKern.flush();
        toKern.close();
        int code = fromKern.read(); // blocks until input
        fromKern.close();
        Files.deleteIfExists(k2a);
        if (code == -1)
            throw new IOException("Encountered EOS while reading from kernel.");
        MessageType mt = MessageType.fromCode((byte) code);
        if (mt == MessageType.CSGRANT) {
            return;
        } else {
            throw new IOException("Encountered unexpected message type from kernel.");
        }
	}
    
    // Interface to apps to exit the critical section.
    public static void csExit(int id) throws IOException {
        String home = System.getProperty("user.home");
        Path a2k = Paths.get(home, "aosme", "App" + id + "ToKern" + id + ".cnl");
        OutputStream toKern = Files.newOutputStream(a2k, StandardOpenOption.APPEND,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        toKern.write(MessageType.CSRETURN.toCode());
        toKern.flush();
        toKern.close();
    }
    
    // Interface to apps to notify the kernel of app completion.
    public static void appDone(int id) throws IOException {
        String home = System.getProperty("user.home");
        Path a2k = Paths.get(home, "aosme", "App" + id + "ToKern" + id + ".cnl");
        OutputStream toKern = Files.newOutputStream(a2k, StandardOpenOption.APPEND,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        toKern.write(MessageType.APPDONE.toCode());
        toKern.flush();
        toKern.close();
    }
	
	public void send_message(ByteBuffer message){
		
		logger.info("Sending "+message+" message to"+" "+parent);
		logger.info("message is " + message.asCharBuffer().toString());
		MessageInfo messageInfo = MessageInfo.createOutgoing(null,0);	
		try {
			neighbor_Map.get(parent).client_channel.send(message, messageInfo);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	public static void main(String args[]) throws Exception{
		
		
		int node_id = Integer.parseInt(args[0]);
		//args[1] is host name
		int port = Integer.parseInt(args[2]);
		int parent = Integer.parseInt(args[3]);
		String config_PATH = args[4];
		int num_nodes = Integer.parseInt(args[5]);
		
		Kernel kernel = new Kernel(node_id,port,parent,num_nodes);
		
		Parser.startsWithToken(config_PATH, node_id);
		Parser.parseChildCount(config_PATH, node_id,parent);
		
		Pipe p = Pipe.open();
		kernel.new FileListener(p.sink(), node_id).start();
		kernel.pipein = p.source();
		kernel.pipein.configureBlocking(false);
		kernel.pipein.register(kernel.channel_selector, SelectionKey.OP_READ);
		
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
				
			}
		});
		connection_thread.start();  // Start the thread to call the above run function from the anonymous class

		try {
			kernel.open_all_connections();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		kernel.logger.info("My neighbors");
		for(Neighbor neighbor:kernel.neighbors){
			kernel.logger.info(Integer.toString(neighbor.node_id));
		}
		
		kernel.mainLoop();
	}
	
	private void mainLoop() throws Exception {
	    while (!allNodesDone()) {
	        channel_selector.select();
	        Set<SelectionKey> keys = channel_selector.selectedKeys();
	        for (SelectionKey key : keys) {
	            SelectableChannel ac = key.channel();
	            if (ac instanceof Pipe.SourceChannel) {
	                handleApp( (Pipe.SourceChannel) ac, key);
	            } else if (ac instanceof SctpChannel) {
	                
	            } else {
	                throw new Exception("Unexpected channel type in mainLoop().");
	            }
	        }
	        keys.clear();
	    }
	}
	
	private void handleApp(Pipe.SourceChannel sc, SelectionKey key) throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(2);
        int num_read = sc.read(buf);
        if (num_read == 0) {
            logger.info("Had an empty app stream read.");
            return;
        } else if (num_read == -1) {
            if (done == false) {
                throw new Exception("App stream ended without being done.");
            } else {
                key.cancel(); // stream ended and we were done, no need to monitor further
            }
        } else {
            if (done == true) {
                throw new Exception("Received message from app that was finished.");
            }
            while (buf.hasRemaining()) {
                byte code = buf.get();
                MessageType mt = MessageType.fromCode(code);
                if (mt == MessageType.CSREQUEST) {
                    
                } else if (mt == MessageType.CSRETURN) {
                    
                } else if (mt == MessageType.APPDONE) {
                    
                } else {
                    throw new Exception("Unexpected message type from app.");
                }
            }
        }
	}
	
	private boolean allNodesDone() {
	    if (done == false)
	        return false;
	    for (Neighbor n : neighbors) {
	        if (n.done == false)
	            return false;
	    }
	    return true;
	}
	
	// This thread type's only purpose is to monitor the input file from the application
	// and write anything it gets to the pipe to the kernel. This allows the kernel to
	// use a Selector that can listen to both the network connections and the
	// application.
	private class FileListener extends Thread {
	    private final Pipe.SinkChannel out;
	    private final int id;

	    FileListener(Pipe.SinkChannel out, int id) {
	        this.out = out;
	        this.id = id;
	    }

	    @Override
	    public void run() {
	        String home = System.getProperty("user.home");

	        // ex. ~/aosme/App1ToKern1.cnl
	        Path fpath = Paths.get(home, "aosme", "App" + id + "ToKern" + id + ".cnl");
	        InputStream in = null;
            try {
                in = Files.newInputStream(fpath,
                        StandardOpenOption.READ,    // open for reading
                        StandardOpenOption.CREATE,  // create iff it doesn't exist
                        // delete on close (may not work properly here since never closed; TODO?)
                        StandardOpenOption.DELETE_ON_CLOSE);
            } catch (IOException e) {
                // TODO: Might need to pass a reference to the logger. Are these messages visible?
                System.err.println("Input channel from app could not be opened.");
                System.exit(1);
            }
            byte[] buf = new byte[20];
            // for now this just runs indefinitely until the process is killed (by main or something else)
	        while (true) {
	            int numRead = 0;
                try {
                    numRead = in.read(buf);
                } catch (IOException e) {
                    System.err.println("Could not read from app channel!");
                    System.exit(1);
                }
                ByteBuffer bbuf = ByteBuffer.wrap(buf, 0, numRead);
                bbuf.flip(); // constrains the buffer to what was read, making it ready to be written; not a literal flip
                try {
                    out.write(bbuf);
                } catch (IOException e) {
                    System.err.println("Could not write to kernel's pipe!");
                    System.exit(1);
                }
	        }
	    }
	}

}
