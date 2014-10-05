package server;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.nio.*;
import java.io.*;

import util.StringUtils;

public class GameRoom {
	// room states
	public final static int ROOM_WAITING = 1;
	public final static int ROOM_PLAYING = 2;
	
	/**
	 * The room id
	 */
	private int id;
	
	/**
	 * The room's master, refers to the player's id
	 */
	private int masterId;
	
	/**
	 * The room's name
	 */
	private String name;
	
	/**
	 * The room state
	 */
	private int state;
	
	/**
	 * The game
	 */
	private Game game;
	
	/**
	 * Players connected to the room
	 */
	private List<Player> connectedPlayers;
	
	private Player player1;
	
	private Player player2;
	
	public GameRoom(int masterId, String name) {
		this.name = name;
		this.masterId = masterId;
		
		connectedPlayers = new LinkedList<>();
		
		Player master = Server.getInstance().getPlayer(masterId);
		assert(master != null);
		connectedPlayers.add(master);
		
		state = ROOM_WAITING;
	}
	
	/**
	 * Sets the room's id
	 * @param id the room's id
	 */
	public void setId(int id) {
		this.id = id;
	}
	
	/**
	 * Gets the room's id
	 * @return the room's id
	 */
	public int getId() {
		return id;
	}
	
	public void setMasterId(int id) {
		this.masterId = id;
	}
	
	public int getMasterId() {
		return masterId;
	}
	
	public int getState() {
		return state;
	}
	
	public Game getGame() {
		return game;
	}
	
	/**
	 * Gets the room's name as a {@link String}
	 * @return the room's name
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Sets the room's name
	 * @param name the room's name, as a String
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * Add player to the room.
	 * This will cause a message to be broadcasted to all the other players, that
	 * a new player is joining
	 * 
	 * The packet structure:
	 * 		[4 byte -- packet code][4 byte -- player id][4 byte -- player name]
	 * @param player the new player
	 * @throws IOException 
	 */
	public void addPlayer(Player player) throws IOException {
		// prepare the response to be broadcasted to all players connected
		ByteBuffer bb = ByteBuffer.allocate(1024);
		
		bb.putInt(Server.ROOM_PLAYER_JOINED);
		bb.putInt(player.getId());
		
		byte[] playerName = player.getName().getBytes();
		for(int i = 0; i < StringUtils.NAME_MAXLENGTH; i++) {
			bb.put(i < playerName.length ? playerName[i] : 0x00);
		}
		
		broadcastMessage(bb);
		
		// add the player after the response
		connectedPlayers.add(player);
	}
	
	/**
	 * Removes a player from the room
	 * @param player
	 * @throws IOException 
	 */
	public void removePlayer(Player player) throws IOException {
		int targetPlayerLocation = connectedPlayers.indexOf(player);
		connectedPlayers.remove(targetPlayerLocation);
		player.setConnectedRoom(Player.NO_ROOM);
		player.setReady(false);
		
		// prepare the response to be broadcasted to all players connected
		ByteBuffer bb = ByteBuffer.allocate(1024);
				
		bb.putInt(Server.ROOM_PLAYER_DISCONNECTED);
		bb.putInt(player.getId());
		
		broadcastMessage(bb);
	}
	
	/**
	 * Sends a list of all players connected to this room
	 * 
	 * Packet structure:
	 * 		[4 byte -- packet code][4 byte -- player id][2 byte -- master flag][32 byte -- player name][rest -- null]
	 * 		master flag --> 'M' if the player is the room's master, else it's S.
	 * @param connection
	 * 				Connection to client
	 * @throws IOException
	 */
	public void queryPlayers(Socket connection) throws IOException {
		Server server = Server.getInstance();
		
		ByteBuffer bb = ByteBuffer.allocate(1024);
		for(Player p : connectedPlayers) {
			bb.putInt(Server.ROOM_PLAYER_INFO).putInt(p.getId());
			
			char masterFlag = isPlayerMaster(p, this) ? 'M' : 'S';
			bb.putChar(masterFlag);
			
			bb.put(p.getName().getBytes());
			
			server.sendResponse(bb.array(), connection);
			bb.clear();
		}
	}
	
	/**
	 * Melempar pesan ke semua player yang terhubung pada GameRoom ini
	 * @param bb ByteBuffer
	 * @throws IOException 
	 */
	public void broadcastMessage(ByteBuffer bb) throws IOException {
		for(Player p: connectedPlayers) {
			OutputStream os = new DataOutputStream(new BufferedOutputStream(p.getConnection().getOutputStream()));
			
			os.write(bb.array());
			os.flush();
		}
	}
	
	/**
	 * Get the number of connected players in this room
	 * @return number of connected players
	 */
	public int getPlayerCount() {
		return connectedPlayers.size();
	}
	
	/**
	 * Disconnects all player from this room.
	 * @throws IOException 
	 */
	public void destroy() throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(1024);
		
		bb.putInt(Server.ROOM_DESTROYED);
		
		// send the package
		broadcastMessage(bb);
		
		// disconnect all players
		for(Player p: connectedPlayers) {
			p.setReady(false);
			p.setConnectedRoom(Player.NO_ROOM);
		}
		connectedPlayers.clear();
	}
	
	/**
	 * Sets the readiness state of a player
	 * NOTE: this assumes that such player exists and is connected to this room
	 * @param player the selected player
	 * @throws IOException 
	 */
	public void setPlayerReady(Player player) throws IOException {
		assert(connectedPlayers.contains(player));
		
		player.setReady(true);
		if(player1 == null) {
			player1 = player;
		} else if(player2 == null) {
			player2 = player;
		} else {
			throw new IOException("Already two players are ready!");
		}
		
		// prepare the response
		ByteBuffer bb = ByteBuffer.allocate(1024);
		
		bb.putInt(Server.ROOM_PLAYER_INFO_READY);
		bb.putInt(player.getId());
		
		broadcastMessage(bb);
	}
	
	public void startGame() throws IOException {
		assert(player1 != null && player2 != null);
		game = new Game(this, player1, player2);
		
		// TODO:
		// 1. broadcast that the game is going to be started
		// 2. change the room state: other connections are going to be refused
		// 3. initiate game.start()
		// 4. sit back and relax~
		
		ByteBuffer bb = ByteBuffer.allocate(1024);
		bb.putInt(Server.ROOM_GAME_START).putInt(id);
		
		broadcastMessage(bb);
		game.start();
	}
	
	/**
	 * Checks if a player is the master in a certain room
	 * @param p
	 * @param room
	 * @return true if the aforementioned player is the master of the room.
	 */
	public static boolean isPlayerMaster(Player p, GameRoom room) {
		return p.getId() == room.getMasterId();
	}

	public int getReadyCount() {
		int count = 0;
		for(Player p : connectedPlayers) {
			if(p.isReady()) {
				count++;
			}
		}
		return count;
	}
}
