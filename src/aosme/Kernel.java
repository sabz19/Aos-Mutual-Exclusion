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
import java.nio.BufferUnderflowException;
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
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
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
	boolean[] done;
	
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
    
    private static int messages_sent = 0;
    private static boolean protocol_started = false;
	
	/*
	 *  Constructor to initialize all related objects
	 */
	public Kernel(int node_id,int port,int parent,int num_nodes) throws IOException{
		
		this.node_id = node_id;
		this.port = port;
		this.parent = parent;
		this.num_nodes = num_nodes;
		
		done = new boolean[num_nodes];
		for (int i = 0; i < num_nodes; i++) {
		    done[i] = false;
		}
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
		
		logger.info("My node_id: "+node_id);
		logger.info("My port: "+port);
		logger.info("My parent: "+parent);
		
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
		
		public Neighbor(int node_id,String host_name,int port) throws UnknownHostException{
			
			this.node_id = node_id;
			serverAddress = new InetSocketAddress(InetAddress.getByName(host_name),port);
			
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
			serverChannel.register(channel_selector, SelectionKey.OP_READ, neighbor_Map.get((int) b.get(0)));
			
			i++;
		}	
	}
	
	private void wait_for_start() throws Exception {
	    
        channel_selector.select();
        Set<SelectionKey> keys = channel_selector.selectedKeys();
        for (SelectionKey key : keys) {
            SelectableChannel ac = key.channel();
            if (ac instanceof SctpChannel) {
                SctpChannel sc = (SctpChannel) ac;
                ByteBuffer b = ByteBuffer.allocate(1);
                MessageInfo mi = sc.receive(b, null, null);
                if (mi == null)
                    continue;
                MessageType mt = MessageType.fromCode(b.get(0));
                if (mt == MessageType.NETSTART) {
                    break;
                } else {
                    throw new Exception("Unexpected message type during network start phase.");
                }
            } else {
                throw new Exception("Unexpected channel type in network start phase.");
            }
        }
        keys.clear();
        
	}
	
	private static void initAppLoggers(int id) throws SecurityException, IOException {
	    regular_file = new FileHandler(logs.toString() + System.getProperty("file.separator") + "App" + id +".log");
        critical_section_file = new FileHandler(logs.toString() + System.getProperty("file.separator") + "critical" + System.getProperty("file.separator") + "App" + id +".log");
        
        SimpleFormatter formatter = new SimpleFormatter();
        regular_file.setFormatter(formatter);
        critical_section_file.setFormatter(formatter);
        
        logger = Logger.getLogger("Regular_Log");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.INFO);
        logger.addHandler(regular_file);
        
        critical_section_logger = Logger.getLogger("Crit_Log");
        critical_section_logger.setUseParentHandlers(false);
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
        System.out.println("Granting request. Timestamp: " + token_ts);
        for (Handler h : critical_section_logger.getHandlers()) {
            h.flush();
        }
		token_in_use = true;
		toApp.write(MessageType.CSGRANT.toCode());
		toApp.flush();
	}
	
	
	private static Instant start;
	private static Instant end;
	private static boolean firstCsEntry = true;
	// Interface to apps to enter the critical section. ID of the app is used
	// to determine what file (channel) to access the kernel with.
	public static void csEnter(int id) throws Exception {
		start = Instant.now();
	    if (firstCsEntry) {
	    	getPaths();
	    	initAppLoggers(id);
	        initKernelConnections(id);
	        firstCsEntry = false;
	    }
        toKern.write(MessageType.CSREQUEST.toCode());
        toKern.flush();
        logger.info("Wrote "+MessageType.CSREQUEST);
        int code = fromKern.read();
        while (code == -1) {
            waitForFileChange(appWatcher, "Kern" + id + "ToApp" + id + ".cnl");
            code = fromKern.read();
            if (code == -1) {
                logger.info("At least two empty reads from the kernel.");
            }
        }
        MessageType mt = MessageType.fromCode((byte) code);
        logger.info("Read " + mt);
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
        logger.info("Wrote "+MessageType.CSRETURN);
        end = Instant.now();
        System.out.println("Response time: " + Duration.between(start, end).toMillis() + " milliseconds");
    }
    
    // Interface to apps to notify the kernel of app completion.
    public static void appDone(int id) throws IOException {
    	toKern.write(MessageType.APPDONE.toCode());
        toKern.flush();
        closeKernelConnections();
        logger.info("Wrote "+MessageType.APPDONE);
    }
	
	public void send_buffer(ByteBuffer message, int nbr_id){
		MessageInfo messageInfo = MessageInfo.createOutgoing(null,0);	
		try {
			neighbor_Map.get(nbr_id).client_channel.send(message, messageInfo);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
    
    public void send_netbuild() {
        ByteBuffer mbuf = ByteBuffer.allocate(1);
        mbuf.put(MessageType.NETBUILD.code);
        mbuf.flip();
        send_buffer(mbuf, parent);
    }
    
    public void send_netstart(int nbr_id) {
        ByteBuffer mbuf = ByteBuffer.allocate(1);
        mbuf.put(MessageType.NETSTART.code);
        mbuf.flip();
        send_buffer(mbuf, nbr_id);
    }
	
	public void send_request() {
	    if (node_id == parent) {
	        return;
	    }
	    logger.info("Sending request to " + parent);
        ByteBuffer mbuf = ByteBuffer.allocate(1);
        mbuf.put(MessageType.REQUEST.code);
        mbuf.flip();
        send_buffer(mbuf, parent);
        if (protocol_started) {
            messages_sent++;
        }
	}
	
	public void send_token(int nbr_id) {
	    logger.info("Sending token to " + nbr_id);
	    ByteBuffer mbuf = ByteBuffer.allocate(5);
        mbuf.put(MessageType.TOKEN.code);
        mbuf.putInt(token_ts);
        mbuf.flip();
        send_buffer(mbuf, nbr_id);
        parent = nbr_id;
        if (protocol_started) {
            messages_sent++;
        }
	}
	
	public void send_node_done(int finished_id, int nbr_id) {
	    logger.info("Notifying node " + nbr_id + " of App " + finished_id +"'s completion.");
        ByteBuffer mbuf = ByteBuffer.allocate(2);
        mbuf.put(MessageType.NODEDONE.code);
        mbuf.put( (byte) finished_id);
        mbuf.flip();
        send_buffer(mbuf, nbr_id);
	}
	
	
	
	private void mainLoop() throws Exception {
	    protocol_started = true;
	    while (!allNodesDone()) {
            channel_selector.select();
	        Set<SelectionKey> keys = channel_selector.selectedKeys();
	        for (SelectionKey key : keys) {
	            SelectableChannel ac = key.channel();
	            if (ac instanceof Pipe.SourceChannel) {
	                // logger.info("Selected the pipe");
	                handleApp( (Pipe.SourceChannel) ac, key);
	            } else if (ac instanceof SctpChannel) {
                    // logger.info("Selected a socket");
	                handleNbr( (SctpChannel) ac, key);
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
            if (done[node_id] == false) {
                throw new Exception("App stream ended without being done.");
            } else {
                key.cancel(); // stream ended and we were done, no need to monitor further
            }
        } else {
            buf.flip();
            if (done[node_id] == true) {
                throw new Exception("Received message from app that was finished.");
            }
            while (buf.hasRemaining()) {
                byte code = buf.get();
                MessageType mt = MessageType.fromCode(code);
                if (mt == MessageType.CSREQUEST) {
                    if (hasToken() && request_queue.isEmpty()) {
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
                    critical_section_logger.info("Received token from app.");
                    token_in_use = false;
                    token_ts++;
                    if (!hasToken()) {
                        throw new Exception("App was in critical section while kernel did not have the token!");
                    }
                    if (!request_queue.isEmpty()) {
                        handleTokenGain();
                    }
                } else if (mt == MessageType.APPDONE) {
                    done[node_id] = true;
                    key.cancel();
                    for (Neighbor nbr : neighbors) {
                        send_node_done(node_id, nbr.node_id);
                    }
                } else {
                    throw new Exception("Unexpected message type from app: " + code);
                }
            }
        }
	}
	
	private void handleNbr(SctpChannel sc, SelectionKey key) throws Exception {
		
	    Neighbor nbr = (Neighbor) key.attachment();
	    ByteBuffer buf = ByteBuffer.allocate(5); // upper bound on amount sent: TOKEN message
	    MessageInfo mi = sc.receive(buf, null, null);
	    while (mi != null) {
	        buf.flip();
	        byte code;
	        try{ 
	            code = buf.get();
	        } catch (BufferUnderflowException e) {
	            logger.info("Received empty message from " + nbr.node_id);
	            buf.clear();
	            mi = sc.receive(buf, null, null);
	            continue;
	        }
    	    MessageType mt = MessageType.fromCode(code);
    	    if (mt == MessageType.REQUEST) {
    	        logger.info("Received REQUEST from " + nbr.node_id);
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
                logger.info("Received TOKEN from " + nbr.node_id);
    	        token_ts = buf.getInt();
    	        handleTokenGain();
    	    } else if (mt == MessageType.NODEDONE) {
    	        int done_id = (int) buf.get();
                logger.info("Received NODEDONE from " + nbr.node_id + " on behalf of App " + done_id);
    	        for (Neighbor neighbor : neighbors) {
    	            if (nbr.node_id != neighbor.node_id) {
    	                send_node_done(done_id, neighbor.node_id);
    	            }
    	        }
    	        done[done_id] = true;
    	    } else {
    	        throw new Exception("Unexpected message type from neighbor: " + mt);
    	    }
    	    buf.clear();
    	    mi = sc.receive(buf, null, null);
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
	    for (int i = 0; i < num_nodes; i++) {
	        if (done[i] == false) {
	            return false;
	        }
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
                    return;
                }
                
                if (numRead == -1) {
                    
                    try {
                        waitForFileChange(watcher, "App" + id + "ToKern" + id + ".cnl");
                    } catch (InterruptedException e) {
                        logger.info("Kernel's watcher was interrupted. Assuming it is time to exit.");
                        return;
                    }
                    
                } else if (numRead == 0) {
                    System.err.println("Read length 0 from the input stream?");
                    return;
                } else {
                    ByteBuffer bbuf = ByteBuffer.wrap(buf, 0, numRead);
                    try {
                        String bstring = "";
                        for (int i = 0; i < bbuf.limit(); i++) {
                            bstring += bbuf.get(i) + " ";
                        }
                        // logger.info("FileListener sent the following through the pipe: " + bstring);
                        out.write(bbuf);
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.err.println("Could not write to kernel's pipe!");
                        return;
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
		
		String nbrstr = "";
		for(Neighbor neighbor : neighbors){
			nbrstr += neighbor.node_id + " ";
		}
		logger.info("My neighbors: " + nbrstr);
		
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
		
		connection_thread.join();
		
		if (neighbors.size() == 1 && !kernel.hasToken()) {
		    logger.info("This node has only 1 neighbor.");
		    // kernel.send_netbuild();
		} else {
		    int readies = 0;
		    if (kernel.hasToken()) {
		        readies--;
		    }
	        while (readies != neighbors.size() - 1) {
	            logger.info("Readies: " + readies + "; Needed: " + neighbors.size());
	            kernel.channel_selector.select();
	            Set<SelectionKey> keys = kernel.channel_selector.selectedKeys();
	            for (SelectionKey key : keys) {
	                SelectableChannel ac = key.channel();
	                if (ac instanceof SctpChannel) {
	                    SctpChannel sc = (SctpChannel) ac;
	                    ByteBuffer b = ByteBuffer.allocate(1);
	                    MessageInfo mi = sc.receive(b, null, null);
	                    if (mi == null)
	                        continue;
	                    MessageType mt = MessageType.fromCode(b.get(0));
	                    if (mt == MessageType.NETBUILD) {
	                        logger.info("NETBUILD received from " + ((Neighbor) key.attachment()).node_id); 
	                        readies++;
	                    } else {
	                        throw new Exception("Unexpected message type during network build phase.");
	                    }
	                } else {
	                    throw new Exception("Unexpected channel type in network build phase.");
	                }
	            }
	            keys.clear();
	        }
		}
		
		if (kernel.hasToken()) {
		    logger.info("Sending NETSTART from root.");
		    for (Neighbor nbr : neighbors) {
		        kernel.send_netstart(nbr.node_id);
		    }
		} else {
		    logger.info("Sending NETBUILD");
            kernel.send_netbuild();
            kernel.wait_for_start();
            logger.info("Sending NETSTART from other node.");
            for (Neighbor nbr : neighbors) {
                if (nbr.node_id != kernel.parent) {
                    kernel.send_netstart(nbr.node_id);
                }
            }
		}
        
        Pipe p = Pipe.open();
        FileListener fl = kernel.new FileListener(p.sink(), fromApp, node_id);
        fl.start();
        kernel.pipein = p.source();
        kernel.pipein.configureBlocking(false);
        kernel.pipein.register(kernel.channel_selector, SelectionKey.OP_READ);
		
        Instant t1 = Instant.now();
		kernel.mainLoop();
		Instant t2 = Instant.now();
		System.out.println("Time network active: " + Duration.between(t1, t2).getSeconds() + " seconds");
		
		fl.interrupt();
		fl.join();
		closeAppConnections();
		System.out.println("Messages sent: " + messages_sent);
	}
	

}
