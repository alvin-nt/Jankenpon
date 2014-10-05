package server;
import java.util.concurrent.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.io.*;

import util.StringUtils;

/**
 * Kelas yang menangani koneksi client ke server.
 * Setiap thread dianggap sebagai sebuah player.
 * 
 * @author alvin_nt
 *
 */
public class Player implements Callable<Void> {
	public static final int NO_ROOM = -1;
	
	private Socket connection;
	
	private DataOutputStream os;
	private DataInputStream is;
	
	private String name;
	private int id;
	
	private boolean ready;
	
	private boolean connected = false;
	
	private int connectedRoom;
	
	public static Player build() {
		return new Player();
	}
	
	public Player(Socket conn) throws IOException {
		assert(conn.isConnected());
		
		setConnection(conn);
		initialize();
	}
	
	private Player() {
		initialize();
	}
	
	private void initialize() {
		id = -1;
		connectedRoom = NO_ROOM;
		ready = false;
		connected = true;
	}

	public void setConnection(Socket conn) throws IOException {
		this.connection = conn;
		
		os = new DataOutputStream(conn.getOutputStream());
		is = new DataInputStream(conn.getInputStream());
		
		System.out.println("New client connected");
		System.out.println("from " + conn.getRemoteSocketAddress().toString());
	}
	
	public Player connection(Socket conn) throws IOException {
		setConnection(conn);
		return this;
	}
	
	public Socket getConnection() {
		return connection;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public void setReady(boolean ready) {
		this.ready = ready;
	}
	
	public boolean isReady() {
		return ready;
	}
	
	public Player id(int id) {
		setId(id);
		return this;
	}
	
	public int getId() {
		return id;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public Player name(String name) {
		setName(name);
		return this;
	}
	
	public String getName() {
		return name;
	}
	
	@Override
	public Void call() throws Exception {
		System.out.println(Thread.currentThread().getName() + " started.");
		while(connected) {
			handleCommand();
		}
		System.out.println("end.");
		return null;
	}
	
	/**
	 * Read the data sent by the client
	 * Buffer structure:
	 * 		[4 byte -- Packet code][1020 byte -- Data]
	 * @param connection
	 * @throws IOException
	 */
	public synchronized void handleCommand() throws IOException {
		byte[] buffer = new byte[1024];
	
		is.read(buffer);
		
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		int code = bb.getInt();
		
		System.out.println("Get code: " + code);
		switch(code) {
		case Server.PLAYER_NAME_SET:
			setPlayerName(bb);
			break;
		case Server.PLAYER_CREATE_ROOM:
			addRoom(bb);
			break;
		case Server.PLAYER_DESTROY_ROOM:
			deleteRoom(bb);
			break;
		case Server.PLAYER_JOIN_ROOM:
			addPlayerToRoom(bb);
			break;
		case Server.PLAYER_QUERY_ROOM:
			System.out.println("Sending room list to " + connection.getLocalAddress());
			sendRoomList();
			break;
		case Server.PLAYER_DISCONNECT:
			disconnect();
			break;
		case Server.ROOM_QUERY_PLAYERS:
			int roomId = bb.getInt();
			GameRoom target = Server.getInstance().rooms.get(roomId);
			
			if(target != null) {
				target.queryPlayers(connection);
			} else {
				// prepare error statement here
			}
			
			break;
		case Server.ROOM_PLAYER_READY:
			setPlayerReady(bb);
			break;
		case Server.ROOM_GAME_START:
			startGame(bb);
			break;
		default:
			os.write("Unknown command!".getBytes());
			os.flush();
		}
	}

	/**
	 * Disconnects the player from the {@link Server}
	 * @param id
	 * @throws IOException
	 */
	private void disconnect() throws IOException {
		connection.close();
		
		Server server = Server.getInstance();
		
		if(connectedRoom != NO_ROOM) {
			GameRoom room = server.rooms.get(connectedRoom);
			room.destroy();
			server.rooms.remove(connectedRoom);
		}
		
		server.players.remove(id);
	}
	
	/**
	 * Add a certain player to a {@link GameRoom}
	 * @param bb
	 * 			ByteBuffer, with this structure:
	 * 			[4 byte -- packet code][4 byte -- GameRoom id]
	 * @param connection
	 * 			Socket connection to {@link Player}
	 */
	private synchronized void addPlayerToRoom(ByteBuffer bb) 
	throws IOException 
	{
		// 1. check if the player exists
		// 2. check if the player has not connected to any room
		// 3. check if the room exists
		// 4. connect the player if all of the conditions above are met.
		
		ByteBuffer bbout = ByteBuffer.allocate(1024);
		
		int playerId = bb.getInt();
		System.out.println("Got request from player " + playerId);
		Player target = Server.getInstance().players.get(playerId);
		if(target != null) {
			if(target.getConnectedRoom() == Player.NO_ROOM) {
				GameRoom room = Server.getInstance().rooms.get(bb.getInt());
				
				if(room != null) {
					// do something here
					room.addPlayer(target);
					target.setConnectedRoom(room.getId());
					
					// prepare confirm statement here
					bbout.putInt(Server.PLAYER_JOIN_ROOM_SUCCESS);
				} else {
					// prepare error statement here
					bbout.putInt(Server.PLAYER_JOIN_ROOM_FAIL);
					
					byte[] message = "Room not found!".getBytes();
					for(int i = 0; i < StringUtils.MESSAGE_MAXLENGTH; i++) {
						bbout.put(i < message.length ? message[i] : 0x00);
					}
				}
			} else {
				// prepare error statement here
				bbout.putInt(Server.PLAYER_JOIN_ROOM_FAIL);
				
				byte[] message = "Player has been connected to another room!".getBytes();
				for(int i = 0; i < StringUtils.MESSAGE_MAXLENGTH; i++) {
					bbout.put(i < message.length ? message[i] : 0x00);
				}
			}
		} else {
			// prepare error statement heretime
			bbout.putInt(Server.PLAYER_JOIN_ROOM_FAIL);
			
			byte[] message = "Player not found!".getBytes();
			for(int i = 0; i < StringUtils.MESSAGE_MAXLENGTH; i++) {
				bbout.put(i < message.length ? message[i] : 0x00);
			}
		}
		
		sendResponse(bbout.array());
	}

	private void sendResponse(byte[] array) throws IOException {
		os.write(array);
		os.flush();
	}

	/**
	 * Add player to the server
	 * @param bb ByteBuffer, that wraps the data written to the {@link Server}
	 * 			the data structure is as follows:
	 * 			
	 * @param connection
	 * @throws IOException
	 */
	private synchronized void setPlayerName(ByteBuffer bb) throws IOException {
		name = new String(bb.array(), 4, StringUtils.NAME_MAXLENGTH);
		
		System.out.println("Received player name: " + name);
		// prepare the response
		ByteBuffer bbout = ByteBuffer.allocate(1024);
		
		bbout.putInt(Server.PLAYER_REGISTERED).putInt(id);
		
		sendResponse(bbout.array());
	}
	
	/**
	 * Removes a player from the server.
	 * @param id
	 */
	public void removePlayer(int id) {
		Player target = Server.getInstance().players.get(id);
		if(target != null) {
			Server.getInstance().players.remove(id);
		}
	}
	
	/**
	 * Create a room
	 * @param buffer
	 * 			buffer byte yang ditulis oleh user. Strukturnya adalah sebagai berikut:
	 * 			[4 byte -- packet code][4 byte -- user ID][32 byte -- room name][sisanya -- kosong]
	 * @throws IOException
	 */
	private void addRoom(ByteBuffer bb) throws IOException {
		assert(bb.position() == 4);
		Integer masterId = bb.getInt();
		String name = StringUtils.getName(bb);
		
		// create the room
		GameRoom room = new GameRoom(masterId, name);
		room.setId(Server.roomId);
		
		Server.getInstance().rooms.put(Server.roomId, room);
		
		System.out.println("Created new room");
		System.out.println("ID: " + Server.roomId);
		System.out.println("Master ID: " + masterId);
		System.out.println("Name: " + room.getName());
		
		// prepare the response
		ByteBuffer bbout = ByteBuffer.allocate(1024);
			
		bbout.putInt(Server.PLAYER_CREATE_ROOM_SUCCESS).putInt(Server.roomId);
		
		sendResponse(bbout.array());
		
		Server.roomId++;
	}
	
	/**
	 * Deletes a room
	 * @param bb
	 * 			ByteBuffer, with this structure:
	 * 			[4 byte -- packet code][4 byte -- room id][4 byte -- player id]
	 * @param connection
	 * 			Socket connection to {@link Player}
	 */
	private void deleteRoom(ByteBuffer bb) throws IOException {
		assert(bb.position() == 4);
		GameRoom target = Server.getInstance().rooms.get(bb.getInt());
		
		if(target != null) {
			Player master = Server.getInstance().players.get(bb.getInt());
			if(master != null) {
				if(master.getConnectedRoom() == target.getMasterId()) {
					target.destroy();
					Server.getInstance().rooms.remove(target.getId());
				} else {
					// prepare error statement
				}
			} else {
				// prepare error statement
			}
		} else {
			// prepare error statement
		}
	}
	
	/**
	 * Sends the GameRoom list to the Client
	 * Struktur data:
	 * 			[4 byte -- kode header][4 byte -- ID room][32 byte -- nama room]
	 * 			[4 byte -- no. of connected players][4 byte -- room master's id]
	 * 			[4 byte -- room state][else -- null]
	 * @param connection koneksi ke Client
	 * @throws IOException 
	 */
	private void sendRoomList() throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(1024);
		for(int i = 0; i < Server.roomId; i++) {
			GameRoom room = Server.getInstance().rooms.get(i);
			
			// GameRoom exists
			if(room != null) {
				System.out.println("Sending room " + room.getId() + " info");
				bb.putInt(Server.PLAYER_ROOM_INFO);
				bb.putInt(room.getId());
				
				byte[] name = room.getName().getBytes();
				for(int j = 0; j < StringUtils.NAME_MAXLENGTH; j++) {
					bb.put(j < name.length ? name[j] : 0x00); 
				}
				
				bb.putInt(room.getPlayerCount());
				bb.putInt(room.getMasterId());
				bb.putInt(room.getState());
				
				os.write(bb.array());
				os.flush();
				
				System.out.println("Room " + room.getId() + " sent");
				bb.clear();
			}
		}
	}
	
	/**
	 * Sets that a player is ready
	 * @param bb
	 * @param connection
	 * @throws IOException
	 */
	private void setPlayerReady(ByteBuffer bb) throws IOException {
		// check if the player is in the room
		Player target = Server.getInstance().players.get(bb.getInt());
		
		if(target != null) {
			int roomId = target.getConnectedRoom();

			if(roomId != Player.NO_ROOM) {
				GameRoom room = Server.getInstance().rooms.get(roomId);
				
				if(room != null) {
					if(room.getReadyCount() < 2) {
						room.setPlayerReady(target);
					}
					
					
					// NOTE: the response is handled by the above method.
				}
			}
		}
	}
	
	/**
	 * Memulai game
	 * @param bb
	 * @throws IOException 
	 */
	private void startGame(ByteBuffer bb) throws IOException {
		int playerId = bb.getInt();
		int roomId = bb.getInt();
		
		Player master = Server.getInstance().players.get(playerId);
		if(master != null) {
			GameRoom room = Server.getInstance().rooms.get(roomId);
			
			if(room != null) {
				room.startGame();
			}
		}
	}

	public void setConnectedRoom(int id) {
		connectedRoom = id;
		
		// TODO: broadcast?
	}
	
	public int getConnectedRoom() {
		return connectedRoom;
	}
}
