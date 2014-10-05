package client;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

import server.GameRoom;
import server.Server;
import util.StringUtils;

public class Client implements Runnable {
	public static final int DEFAULT_PORT = 8094;
	
	private Socket connection;
	
	// player data
	private int playerId;
	private String name;
	
	// room data
	private int roomId;
	private boolean ready;
	
	private Scanner s;
	
	private ByteBuffer bb = ByteBuffer.allocate(1024);
	
	private DataInputStream is;
	private DataOutputStream os;
	
	private final int mainMenu_CreateRoom = 1;
	private final int mainMenu_ListRoom = 2;
	private final int mainMenu_joinRoom = 3;
	private final int mainMenu_exit = 4;
	
	private String[] mainMenu = new String[] {
			"1. Create room",
			"2. List room",
			"3. Join room",
			"4. Exit"
	};
	
	// the state
	private int state;
	
	private final int STATE_MAIN_MENU = 1;
	private final int STATE_ROOM = 2;
	private final int STATE_GAME = 3;
	
	private final int room_deleteRoom = 1;
	private final int room_ready = 2;
	private final int room_listPlayers = 3;
	private final int room_start = 4;
	
	private String[] roomMenu = new String[] {
		"1. Delete room",
		"2. Set ready",
		"3. View connected players",
		"4. Start game"
	};
	
	private boolean exit = false;
	
	public static void main(String[] args) {
		Client client = new Client();
		client.run();
	}
	
	@Override
	public void run() {
		s = new Scanner(System.in);
		
		System.out.print("Enter the server address: ");
		String serverAddress = s.nextLine();
		
		try {
			connection = new Socket(serverAddress, DEFAULT_PORT);
			
			is = new DataInputStream(connection.getInputStream());
			os = new DataOutputStream(connection.getOutputStream());
			
			registerPlayer();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		while(!exit) {
			try {
				int selection; 
				switch(state) {
				case STATE_MAIN_MENU:
					selection = mainMenu();
					mainMenuHandle(selection);
					break;
				case STATE_GAME:
					// TODO: game~
					break;
				case STATE_ROOM:
					selection = roomMenu();
					roomHandle(selection);
					break;
				}
				
			} catch (IOException e) {
				exit = true;
				e.printStackTrace();
			}
		}
		
		try {
			disconnect();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int roomMenu() {
		int selection = -1;
		while(selection == -1) {
			try {
				System.out.println("Room menu:");
				for(String s: roomMenu) {
					System.out.println(s);
				}
				System.out.print("Your selection: ");
				
				selection = Integer.parseInt(s.nextLine());
			} catch (Exception e) {
				e.printStackTrace();
				selection = -1;
			}
		}
		
		return selection;
	}
	
	private void roomHandle(int selection) {
		System.out.println("NO FUNCTION IMPLEMENTED YET HERE. WELCOME TO ENDLESS LOOP");
		switch(selection) {
		case room_deleteRoom:
			break;
		case room_ready:
			break;
		case room_listPlayers:
			break;
		case room_start:
			break;
		}
	}

	/**
	 * Registers the client as a new player in the server
	 * @throws IOException
	 */
	private void registerPlayer() throws IOException {
		System.out.print("Enter new player name: ");
		name = s.nextLine();
		
		// prepare the response
		bb.putInt(Server.PLAYER_NAME_SET);
		bb.put(name.getBytes());
		os.write(bb.array());
		
		bb.clear();
		
		is.readFully(bb.array());
		// get the player id
		int resp = bb.getInt();
		if(resp == Server.PLAYER_REGISTERED) {
			playerId = bb.getInt();
			System.out.println("Player " + name + " registered with id " + playerId);
			
			state = STATE_MAIN_MENU;
		} else {
			// something wrong
		}
		bb.clear();
	}
	
	private int mainMenu() throws IOException {
		int ret = -1;
		while(ret == -1) {
			try {
				System.out.println("Menu: ");
				for(String s: mainMenu) {
					System.out.println(s);
				}
				System.out.print("Your selection: ");
				
				ret = Integer.parseInt(s.nextLine());
				
				if(ret < 1 || ret > mainMenu.length) {
					throw new Exception("Unknown selection");
				}
			} catch (Exception e) {
				System.out.println(e);
				ret = -1;
			}
		}
		
		return ret;
	}
	
	private void mainMenuHandle(int selection) throws IOException {
		switch(selection) {
		case mainMenu_CreateRoom:
			createRoom();
			break;
		case mainMenu_ListRoom:
			listRoom();
			break;
		case mainMenu_joinRoom:
			try {
				listRoom();
			} catch (IOException e) {
				// ignore
			}
			
			joinRoom();
			break;
		case mainMenu_exit:
			exit = true;
			break;
		}
	}
	
	private void joinRoom() throws IOException {
		System.out.print("Select room id: ");
		int selection = Integer.parseInt(s.nextLine());
		
		bb.putInt(Server.PLAYER_JOIN_ROOM);
		bb.putInt(playerId);
		bb.putInt(selection);
		
		os.write(bb.array());
		os.flush();
		
		bb.clear();
		
		is.read(bb.array());
		int resp = bb.getInt();
		if(resp == Server.PLAYER_JOIN_ROOM_SUCCESS) {
			System.out.println("Successfully joined room " + bb.getInt());
			state = STATE_ROOM;
		} else {
			System.out.print("Error: ");
			System.out.println(StringUtils.getMessage(bb));
		}
	}

	/**
	 * Get the list of rooms available in the server
	 * @throws IOException
	 */
	private void listRoom() throws IOException {
		bb.putInt(Server.PLAYER_QUERY_ROOM);
		
		os.write(bb.array());
		os.flush();
		bb.clear();
		
		connection.setSoTimeout(3000);
		try {
			Thread.sleep(500);
			System.out.println("No.\tRoom Name\t\tConnected Players\tMasterID\tState");
			while(is.read(bb.array()) != -1) {
				int resp = bb.getInt();
				if(resp == Server.PLAYER_ROOM_INFO) {
					StringBuilder sb = new StringBuilder();
					
					int roomId = bb.getInt();
					String roomName = StringUtils.getName(bb);
					
					int connectedPlayers = bb.getInt();
					int roomMasterId = bb.getInt();
					int roomState = bb.getInt();
					String state;
					switch(roomState) {
					case GameRoom.ROOM_WAITING:
						state = "Waiting for players";
						break;
					case GameRoom.ROOM_PLAYING:
						state = "Game running";
						break;
					default:
						state = "Unknown";
					}
					
					// print the room state
					sb.append(roomId).append("\t");
					sb.append(roomName).append("\t");
					sb.append(connectedPlayers).append("\t").append("\t");
					sb.append(roomMasterId).append("\t");
					sb.append(state);
					
					System.out.println(sb.toString());
				}
				
				bb.clear();
			}
		} catch (SocketTimeoutException e) {
			System.out.println("No more room available");
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			throw e;
		} finally {
			connection.setSoTimeout(0);
		}
	}

	private void createRoom() throws IOException {
		System.out.print("Write the name of your room: ");
		String name = s.nextLine();
		
		// prepare
		bb.putInt(Server.PLAYER_CREATE_ROOM).putInt(playerId);
		bb.put(name.getBytes());
		
		// write
		os.write(bb.array());
		os.flush();
		
		// clear
		bb.clear();
		
		// read
		is.readFully(bb.array());
		int resp = bb.getInt();
		if(resp == Server.PLAYER_CREATE_ROOM_SUCCESS) {
			System.out.println("Successfully created new room:");
			System.out.println("ID: " + bb.getInt());
			System.out.println("Name: " + name);
			
			state = STATE_ROOM;
		} else {
			System.out.println("Caught response: " + resp);
			System.out.println("Message: " + StringUtils.getMessage(bb));
		}
		
		bb.clear();
	}
	
	private void disconnect() throws IOException {
		System.out.print("Disconnecting....");
		
		bb.putInt(Server.PLAYER_DISCONNECT);
		
		os.write(bb.array());
		
		os.close();
		is.close();
		s.close();
		connection.close();
	}
}
