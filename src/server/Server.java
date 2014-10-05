package server;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

import util.StringUtils;

public class Server implements Runnable {
	// player codes
	//public static final int PLAYER_JOIN = 61;
	public static final int PLAYER_NAME_SET = 64;
	public static final int PLAYER_REGISTERED = 62;
	
	public static final int PLAYER_JOIN_ROOM = 88;
	public static final int PLAYER_JOIN_ROOM_SUCCESS = 90;
	public static final int PLAYER_JOIN_ROOM_FAIL = 92;
	
	public static final int PLAYER_CREATE_ROOM = 22;
	public static final int PLAYER_CREATE_ROOM_SUCCESS = 24;
	
	public static final int PLAYER_DESTROY_ROOM = 26;
	public static final int PLAYER_DESTROY_ROOM_SUCCESS = 28;
	
	public static final int PLAYER_QUERY_ROOM = 101;
	public static final int PLAYER_ROOM_INFO = 103;
	
	public static final int PLAYER_DISCONNECT = 11;
	public static final int PLAYER_DISCONNECT_OK = 13;
	
	public static final int PLAYER_READY_ROOM = 2;
	
	// room codes
	public final static int ROOM_GAME_CANCEL = 239;
	public final static int ROOM_GAME_END = 233;
	public final static int ROOM_GAME_START = 231;
	
	public final static int ROOM_DESTROYED = 299;
	
	public final static int ROOM_PLAYER_INFO = 223;
	public final static int ROOM_QUERY_PLAYERS = 221;
	
	public final static int ROOM_PLAYER_DISCONNECTED = 213;
	public final static int ROOM_PLAYER_JOINED = 211;
	public final static int ROOM_PLAYER_READY = 215;
	public final static int ROOM_PLAYER_INFO_READY = 217;
	
	// game codes
	public final static int GAME_UPDATE_SELECTION = 311;
	public final static int GAME_WINNER = 313;
	
	public final static int GAME_TIME_UPDATE = 355;
	public final static int GAME_STATE_UPDATE = 357;
	public final static int GAME_SELECTION_UPDATE = 359;
	
	public static int roomId;
	public static int playerId;
	
	private static final int DEFAULT_PORT = 8094;
	
	private ServerSocket serverSocket; 
	
	private List<Future<?>> futureTask;
	
	/**
	 * The game rooms
	 */
	public HashMap<Integer, GameRoom> rooms;

	/**
	 * Connected players
	 */
	public HashMap<Integer, Player> players;
	
	private ScheduledExecutorService playerThreadPool;
	
	public static void main(String[] args) {
		instance.run();
	}
	
	private static Server instance = new Server();
	
	public static Server getInstance() {
		return instance;
	}
	
	private Server() {
		roomId = 0;
		try {
			serverSocket = new ServerSocket(DEFAULT_PORT);
		
			rooms = new HashMap<>();
			players = new HashMap<>();
			
			playerThreadPool = Executors.newScheduledThreadPool(50);
			
			futureTask = new LinkedList<>();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		boolean exit = false;
		
		while(!exit) {
			try {
				Socket connection = serverSocket.accept();
				Player player = Player.build().id(playerId).connection(connection);
				
				players.put(playerId, player);
				
				ScheduledFuture<?> task = playerThreadPool.schedule(player, 100, TimeUnit.MILLISECONDS); 
				futureTask.add(task);
				
				// increase the playerId
				playerId++;
			} catch (Exception e) {
				e.printStackTrace();
				for(Future<?> f: futureTask) {
					if(f.isDone()) {
						futureTask.remove(f);
					}
				}
			}
		}
		
		playerThreadPool.shutdown();
	}
	
	/**
	 * Broadcasts a package across all clients
	 * @param buffer
	 * 			the package
	 * @throws IOException
	 */
	public void broadcastResponse(byte[] buffer) throws IOException {
		for(int i = 0; i < playerId; i++) {
			Player target = players.get(i);
			if(target != null) {
				sendResponse(buffer, target.getConnection());
			}
		}
	}
	
	/**
	 * Mendapatkan player yang terhubung di Server dengan id tertentu
	 * @param id id player
	 * @return Objek player
	 */
	public Player getPlayer(int id) {
		return players.get(id);
	}
	
	/**
	 * Sends an one-time response to the client
	 * @param buffer
	 * 			the data buffer/the package
	 * @param connection
	 * 			client connection
	 * @throws IOException
	 */
	public void sendResponse(byte[] buffer, Socket connection) throws IOException {
		OutputStream os = new DataOutputStream(new BufferedOutputStream(connection.getOutputStream()));
		os.write(buffer);
		os.flush();
	}
}
