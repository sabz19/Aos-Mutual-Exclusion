package aosme;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
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

public class Kernel {
	
	
	Object connection_lock; // lock on connect before allowing client channels to send messages
	private int port,num_nodes,node_id,parent = -1;
	boolean greedy;
	boolean done;
	
	String file = System.getProperty("user.home") + "/aosme/"+node_id+".txt";
	int token_ts;
	boolean token_in_use;
	
	BufferedReader input_reader;
	BufferedWriter output_writer;
	
	Selector channel_selector;
	
	static HashSet<Neighbor> neighbors = new HashSet<>();
	static HashMap<Integer,Neighbor> neighbor_Map = new HashMap<>();
	Queue<Integer> request_queue; //Store all incoming requests in this queue
	
	
	static Logger logger,critical_section_logger;
	static FileHandler regular_file;
    static FileHandler critical_section_file;
	
	private Pipe.SourceChannel pipein;
	
    private static Path a2k;
    private static Path k2a;

    private static InputStream fromKern;
    private static InputStream fromApp;
    private static OutputStream toKern;
    private static OutputStream toApp;
    
    private static Path base;
    private static Path comm;
    private static Path logs;
	
	/*
	 *  Constructor to initialize all related objects
	 */
	public Kernel(int node_id,int port,int parent,int num_nodes) throws IOException{
		
		this.node_id = node_id;
		this.port = port;
		this.parent = parent;
		this.num_nodes = num_nodes;
		
		done = false;
		greedy = false;
		
		connection_lock = new Object();
		request_queue = new LinkedList<>();
		channel_selector = Selector.open();
		//input_reader = new BufferedReader(new FileReader(file));
		//output_writer = new BufferedWriter(new FileWriter(file));
		
		token_ts = 0;
		token_in_use = false;
		
		a2k = Paths.get(comm.toString(),"App" + node_id + "ToKern" + node_id + ".cnl");
	    k2a = Paths.get(comm.toString(),"Kern" + node_id + "ToApp" + node_id + ".cnl");
	    
	    if(!Files.exists(a2k))
	    	Files.createFile(a2k);
	    if(!Files.exists(k2a))
	    	Files.createFile(k2a);
	    
	   
		initAppConnections(node_id);
		
		/*
		 * Start all loggers
		 */
		
		regular_file = new FileHandler(logs.toString() + System.getProperty("file.separator") +"Kern" +node_id+".log");
		critical_section_file = new FileHandler(logs.toString() + System.getProperty("file.separator") + "critical" + System.getProperty("file.separator") + "Kern" +node_id+".log");
		
		SimpleFormatter formatter = new SimpleFormatter();
		regular_file.setFormatter(formatter);
		critical_section_file.setFormatter(formatter);
		
		logger = Logger.getLogger("Regular_Log");
        logger.addHandler(regular_file);
		logger.setUseParentHandlers(false);
		logger.setLevel(Level.INFO);
		
		critical_section_logger = Logger.getLogger("Crit_Log");
        critical_section_logger.addHandler(critical_section_file);
		critical_section_logger.setUseParentHandlers(false);
		critical_section_logger.setLevel(Level.INFO);
		
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
	public HashSet<Neighbor> get_Neighbors(){
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
	
	public static void getPaths() {
        final File kf = new File(Kernel.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        Path kp = kf.toPath();
        base = kp.getParent();
        comm = Paths.get(base.toString(), "comm");
        logs = Paths.get(base.toString(), "logs");
	}
	
	public void open_all_connections() throws InterruptedException, IOException{
		
		
		for(Neighbor neighbor:neighbors){
			
			boolean connection_established = false;
			while(!connection_established){
				try {
					
					neighbor.client_channel = SctpChannel.open();
					neighbor.client_channel.connect(neighbor.serverAddress);
					connection_established = true;
			        ByteBuffer b = ByteBuffer.allocate(1);
		            b.put( (byte) node_id);
		            b.flip();
		            MessageInfo mi = MessageInfo.createOutgoing(null,0);   
		            neighbor.client_channel.send(b, mi); // send id
					
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
			logger.info("Connection established");
            ByteBuffer b = ByteBuffer.allocate(1);
            serverChannel.receive(b, null, null); // receive id
			serverChannel.configureBlocking(false);
			// attach neighbor info to the channel's key
			serverChannel.register(channel_selector, SelectionKey.OP_READ, neighbor_Map.get(b.get(0)));
			
			i++;
		}	
	}
	
	private static void initAppLoggers(int id) throws SecurityException, IOException {
	    regular_file = new FileHandler(logs.toString() + System.getProperty("file.separator") + "App" + id +".log");
        critical_section_file = new FileHandler(logs.toString() + System.getProperty("file.separator") + "critical" + System.getProperty("file.separator") + "App" + id +".log");
        
        SimpleFormatter formatter = new SimpleFormatter();
        regular_file.setFormatter(formatter);
        critical_section_file.setFormatter(formatter);
        
        logger = Logger.getLogger("Regular_Log");
        logger.setLevel(Level.INFO);
        logger.addHandler(regular_file);
        
        critical_section_logger = Logger.getLogger("Crit_Log");
        critical_section_logger.setLevel(Level.INFO);
        critical_section_logger.addHandler(critical_section_file);
	}

	private static void initAppConnections(int id) throws IOException {
		
        a2k = Paths.get(comm.toString(), "App" + id + "ToKern" + id + ".cnl");
        k2a = Paths.get(comm.toString(), "Kern" + id + "ToApp" + id + ".cnl");
        
        
        fromApp = Files.newInputStream(a2k,
                StandardOpenOption.READ);
        toApp = Files.newOutputStream(k2a,
                StandardOpenOption.WRITE);
       
	}
	
	
	private static void closeAppConnections() throws IOException {
		
	    fromApp.close();
	    toApp.close();
	    boolean deleted = false;
	    while (!deleted) {
    	    try {
        	    Files.deleteIfExists(a2k);
        	    Files.deleteIfExists(k2a);
        	    deleted = true;
    	    } catch (IOException e) {
    	        try {
                    Thread.sleep(5);
                } catch (InterruptedException e1) {}
    	    }
	    }
	}

    private static WatchService appWatcher;
    
	private static void initKernelConnections(int id) throws IOException {
		
        a2k = Paths.get(comm.toString(), "App" + id + "ToKern" + id + ".cnl");
        k2a = Paths.get(comm.toString(), "Kern" + id + "ToApp" + id + ".cnl");
        
        if (!Files.exists(a2k)) {
            try {
                Files.createFile(a2k);
            } catch (Exception e) {}
        }
        if (!Files.exists(k2a)) {
            try {
                Files.createFile(k2a);
            } catch (Exception e) {}
        }
		
        fromKern = Files.newInputStream(k2a, 
                StandardOpenOption.READ);
        toKern = Files.newOutputStream(a2k, 
                StandardOpenOption.WRITE);
        appWatcher = FileSystems.getDefault().newWatchService();
        comm.register(appWatcher, StandardWatchEventKinds.ENTRY_MODIFY);
	}
	
	private static void closeKernelConnections() throws IOException {
        toKern.close();
	    fromKern.close();
	}
	
	private boolean hasToken() {
	    if (parent == node_id && token_in_use == false)
	        return true;
	    else
	        return false;
	}
	
	private static void waitForFileChange(WatchService watcher, String fileName) throws InterruptedException {
        while (true) {
            WatchKey key = watcher.take();
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.context().toString().endsWith(fileName)) {
                    key.reset();
                    return;
                }
            }
            key.reset();
        }
    }
	
	// Function called within the kernel to grant permission to the application
	private void csGrant() throws IOException{
		
		critical_section_logger.info("Granting request. Timestamp: " + token_ts);
		token_in_use = true;
		toApp.write(MessageType.CSGRANT.toCode());
		toApp.flush();
	}
	
	private static boolean firstCsEntry = true;
	// Interface to apps to enter the critical section. ID of the app is used
	// to determine what file (channel) to access the kernel with.
	public static void csEnter(int id) throws Exception {
		
	    if (firstCsEntry) {
	    	getPaths();
	    	initAppLoggers(id);
	        initKernelConnections(id);
	        firstCsEntry = false;
	    }
        toKern.write(MessageType.CSREQUEST.toCode());
        
        toKern.flush();
        System.out.println("Wrote"+MessageType.CSREQUEST.toCode());
        int code = fromKern.read();
        if (code == -1) {
            waitForFileChange(appWatcher, "Kern" + id + "ToApp" + id + ".cnl");
            System.out.println("Still waiting on file change");
            code = fromKern.read();
        }
        MessageType mt = MessageType.fromCode((byte) code);
        if (mt == MessageType.CSGRANT) {
        	logger.info("Granted");
            return;
        } else {
            throw new IOException("Encountered unexpected message type from kernel.");
        }
	}
    
    // Interface to apps to exit the critical section.
    public static void csExit(int id) throws IOException {
        toKern.write(MessageType.CSRETURN.toCode());
        toKern.flush();
    }
    
    // Interface to apps to notify the kernel of app completion.
    public static void appDone(int id) throws IOException {
    	toKern.write(MessageType.APPDONE.toCode());
        toKern.flush();
        closeKernelConnections();
    }
	
	public void send_buffer(ByteBuffer message, int nbr_id){
		
		logger.info("Sending "+message+" message to"+" "+nbr_id);
		logger.info("message is " + message.asCharBuffer().toString());
		MessageInfo messageInfo = MessageInfo.createOutgoing(null,0);	
		try {
			neighbor_Map.get(nbr_id).client_channel.send(message, messageInfo);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void send_request() {
        ByteBuffer mbuf = ByteBuffer.allocate(1);
        mbuf.put(MessageType.REQUEST.code);
        mbuf.flip();
        send_buffer(mbuf, parent);
	}
	
	public void send_token(int nbr_id) {
	    ByteBuffer mbuf = ByteBuffer.allocate(5);
        mbuf.put(MessageType.TOKEN.code);
        mbuf.putInt(token_ts);
        mbuf.flip();
        send_buffer(mbuf, nbr_id);
        parent = nbr_id;
	}
	
	public void send_node_done(int finished_id, int nbr_id) {
        ByteBuffer mbuf = ByteBuffer.allocate(2);
        mbuf.put(MessageType.NODEDONE.code);
        mbuf.put( (byte) finished_id);
        mbuf.flip();
        send_buffer(mbuf, nbr_id);
	}
	
	
	
	private void mainLoop() throws Exception {
        logger.info("Entering main loop");
	    while (!allNodesDone()) {
            logger.info("Attempting to select channels");
	        Set<SelectionKey> keys = channel_selector.selectedKeys();
	        for (SelectionKey key : keys) {
	            logger.info("Processing key");
	            SelectableChannel ac = key.channel();
	            if (ac instanceof Pipe.SourceChannel) {
	                logger.info("Selected the pipe");
	                handleApp( (Pipe.SourceChannel) ac, key);
	            } else if (ac instanceof SctpChannel) {
                    logger.info("Selected a socket");
	                handleNbr( (SctpChannel) ac, key);
	            } else {
	                throw new Exception("Unexpected channel type in mainLoop().");
	            }
	        }
	        if (keys.size() == 0) {
	            logger.info("No keys were selected. Waiting.");
	            channel_selector.select();
	        } else if (keys.size() > 0) {
	            keys.clear();
	        }
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
                    if (hasToken() && request_queue.isEmpty()) {
                    	logger.info("I have token entering csGrant");
                        csGrant();
                    } else if (hasToken() && !request_queue.isEmpty()) {
                        throw new Exception("Had token, app was not using it, but queue was nonempty.");
                    } else if (!hasToken() && request_queue.isEmpty()) {
                        request_queue.add(node_id);
                        send_request();
                    } else if (!hasToken() && !request_queue.isEmpty()) {
                        request_queue.add(node_id);
                    }
                } else if (mt == MessageType.CSRETURN) {
                	logger.info("Returning from csReturn");
                    token_in_use = false;
                    token_ts++;
                    if (!hasToken()) {
                        throw new Exception("App was in critical section while kernel did not have the token!");
                    }
                    if (!request_queue.isEmpty()) {
                        handleTokenGain();
                    }
                } else if (mt == MessageType.APPDONE) {
                	logger.info("Done with all apps");
                    done = true;
                    key.cancel();
                    for (Neighbor nbr : neighbors) {
                        send_node_done(node_id, nbr.node_id);
                    }
                } else {
                    throw new Exception("Unexpected message type from app.");
                }
            }
        }
	}
	
	private void handleNbr(SctpChannel sc, SelectionKey key) throws Exception {
		
	    Neighbor nbr = (Neighbor) key.attachment();
	    ByteBuffer buf = ByteBuffer.allocate(2 * 45 + 5 + 1); // upper bound on amount sent
	    sc.receive(buf, null, null);                          // 45 nodes * 2 bytes per done message, 5 bytes for a token, 1 byte for a request
	    logger.info("Ever here??");
	    while (buf.hasRemaining()) {
    	    byte code = buf.get();
    	    MessageType mt = MessageType.fromCode(code);
    	    if (mt == MessageType.REQUEST) {
    	        if (request_queue.isEmpty() && hasToken()) {
    	            send_token(nbr.node_id);
    	        } else if (!request_queue.isEmpty() && hasToken()) {
    	            throw new Exception("Finished previous IO, had a nonempty queue and the token, yet did not do anything with the token.");
    	        } else {
    	            if (request_queue.isEmpty()) {
    	                send_request();
    	            }
    	            request_queue.add(nbr.node_id);
    	        }
    	    } else if (mt == MessageType.TOKEN) {
    	        token_ts = buf.getInt();
    	        handleTokenGain();
    	    } else if (mt == MessageType.NODEDONE) {
    	        int done_id = (int) buf.get();
    	        for (Neighbor neighbor : neighbors) {
    	            if (done_id != neighbor.node_id) {
    	                send_node_done(done_id, neighbor.node_id);
    	            }
    	        }
    	    } else {
    	        throw new Exception("Unexpected message type from neighbor.");
    	    }
	    }
	}
	
	private void handleTokenGain() throws IOException {
	    parent = node_id;
	    if (greedy == true && request_queue.contains(node_id)) {
	        request_queue.remove(node_id);
	        csGrant();
	    } else {
	        int dest_id = request_queue.remove();
	        if (dest_id != node_id) {
	            send_token(dest_id);
	            if (!request_queue.isEmpty()) {
	                send_request();
	            }
	        } else {
	            csGrant();
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
	    private final InputStream in;

	    FileListener(Pipe.SinkChannel out, InputStream fromApp, int id) {
	        this.out = out;
	        this.id = id;
	        this.in = fromApp;
	    }

	    @Override
	    public void run() {
	        WatchService watcher = null;
	        
            try {
                watcher = FileSystems.getDefault().newWatchService();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
	        
	        try {
                comm.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
	        
	        while (!Thread.currentThread().isInterrupted()) {
	            byte[] buf = new byte[20];
	            int numRead = -1;
	            
                try {
                    numRead = in.read(buf);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.err.println("Could not read from app channel!");
                    System.exit(1);
                }
                
                if (numRead == -1) {
                    
                    try {
                        waitForFileChange(watcher, "App" + id + "ToKern" + id + ".cnl");
                        logger.info("Returned");
                    } catch (InterruptedException e) {
                        logger.info("Kernel's watcher was interrupted. Assuming it is time to exit.");
                    }
                    
                } else if (numRead == 0) {
                    System.err.println("Read length 0 from the input stream?");
                    System.exit(1);
                } else {
                    ByteBuffer bbuf = ByteBuffer.wrap(buf, 0, numRead);
                    bbuf.flip(); // constrains the buffer to what was read, making it ready to be written; not a literal flip
                    try {
                        out.write(bbuf);
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.err.println("Could not write to kernel's pipe!");
                        System.exit(1);
                    }
                }
	        }
	    }
	}
	public static void main(String args[]) throws Exception{
	    
	    getPaths();
		
		int node_id = Integer.parseInt(args[0]);
		//args[1] is host name
		int port = Integer.parseInt(args[2]);
		int parent = Integer.parseInt(args[3]);
		String config_PATH = args[4];
		int num_nodes = Integer.parseInt(args[5]);
		
		
		Kernel kernel = new Kernel(node_id,port,parent,num_nodes);
		
		if(args.length > 6 && args[6].equalsIgnoreCase("TRUE"))
			kernel.greedy = true;
		
		logger.info("Greedy: " + kernel.greedy);
		
		Parser.startsWithToken(config_PATH, node_id);
		Parser.parseChildCount(config_PATH, node_id,parent);
		
		logger.info("My neighbors");
		for(Neighbor neighbor : neighbors){
			logger.info(Integer.toString(neighbor.node_id));
		}
		
		Pipe p = Pipe.open();
		FileListener fl = kernel.new FileListener(p.sink(), fromApp, node_id);
		fl.start();
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
		
		
		kernel.mainLoop();
		fl.interrupt();
		connection_thread.join();
		fl.join();
		closeAppConnections();
	}
	

}
