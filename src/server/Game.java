package server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Class that represents the Jankenpon game
 * @author alvin_nt
 *
 */
public class Game {
	// the selections of the game
	public final static int SELECTION_EMPTY = 0;
	public final static int SELECTION_ROCK = 1;
	public final static int SELECTION_PAPER = 2;
	public final static int SELECTION_SCISSORS = 3;
	
	// game states
	public final static int GAME_READY = 0;
	public final static int GAME_START = 1;
	public final static int GAME_FINISH = 2;
	public final static int GAME_CANCELED = -1;
	
	/**
	 * The game time
	 */
	private int state;
	
	private int round;
	
	private int time;
	private Timer timer;
	
	private Player player1;
	private int player1Selection;
	
	private Player player2;
	private int player2Selection;
	
	private GameRoom connectedRoom;
	private ByteBuffer bbMessage;
	
	public Game(GameRoom room, Player player1, Player player2) {
		connectedRoom = room;
		this.player1 = player1;
		this.player2 = player2;
		
		bbMessage = ByteBuffer.allocate(1024);
		
		timer = new Timer();
		
		state = GAME_READY;
	}
	
	public int getSelection(int id, int selection) {
		int ret;
		
		if(player1.getId() == id) {
			ret = player1Selection;
		} else if(player2.getId() == id) {
			ret = player2Selection;
		} else {
			ret = -1; // error~
		}
		
		return ret;
	}
	
	public void updateSelection(int id, int selection) throws IOException {
		if(player1.getId() == id) {
			player1Selection = selection;
		} else if (player2.getId() == id) {
			player2Selection = selection;
		} else {
			throw new IOException("No such player!");
		}
		
		// response
		bbMessage.putInt(Server.GAME_SELECTION_UPDATE);
		bbMessage.putInt(id).putInt(selection);
		
		try {
			connectedRoom.broadcastMessage(bbMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		bbMessage.clear();
	}
	
	private void updateTime(int time) {
		this.time = time;
		if(this.time <= 1) {
			timer.cancel();
			this.time = 0;
		}
		
		bbMessage.putInt(Server.GAME_TIME_UPDATE);
		bbMessage.putInt(connectedRoom.getId()).putInt(time);
		
		try {
			connectedRoom.broadcastMessage(bbMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		bbMessage.clear();
	}
	
	/**
	 * Updates the game state and broadcasts the status to all players connected to the GameRoom.
	 * @param state
	 */
	private void updateState(int state) {
		this.state = state;
		
		bbMessage.putInt(Server.GAME_STATE_UPDATE);
		bbMessage.putInt(connectedRoom.getId()).putInt(this.state);
		
		try {
			connectedRoom.broadcastMessage(bbMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		bbMessage.clear();
	}
	
	public void start() {
		// TODO:
		// 1. broadcast a message to all players in the room
		// 2. start the timer
		// 3. while waiting, get the player's selection
		// 4. invoke end()
		
		updateState(GAME_START);
		
		int delay = 1000;
		int period = 1000;
		
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				updateTime(time-1);
			}
		}, delay, period);
		
		while(time > 0) {
			// get the player's selection here
		}
		
		end();
	}
	
	/**
	 * Ends the game
	 */
	private void end() {
		updateState(GAME_FINISH);
		
		// broadcast the winner
		int winner = getWinner();
		
		bbMessage.putInt(Server.GAME_WINNER);
		bbMessage.putInt(connectedRoom.getId()).putInt(winner);
		
		try {
			connectedRoom.broadcastMessage(bbMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		bbMessage.clear();
	}
	
	/**
	 * Gets the winner of the current round.
	 * @return
	 * 		0 - no winner (draw),
	 * 		1 - player 1 wins,
	 * 		2 - player 2 wins
	 */
	private int getWinner() {
		int ret;
		
		if(player1Selection == player2Selection) { // draw
			ret = 0;
		} else {
			switch(player1Selection) {
			case SELECTION_EMPTY:
				ret = 2; // player 2 must had chosen something in the end.
				break;
			case SELECTION_ROCK:
				if(player2Selection == SELECTION_PAPER) {
					ret = 2;
				} else { // player 2 had selected either nothing or SCISSORS
					ret = 1;
				}
				break;
			case SELECTION_PAPER:
				if(player2Selection == SELECTION_SCISSORS) {
					ret = 2;
				} else { // player 2 had selected either nothing or ROCK
					ret = 1;
				}
				break;
			case SELECTION_SCISSORS:
				if(player2Selection == SELECTION_ROCK) {
					ret = 2;
				} else { // player 2 had selected either nothing or PAPER
					ret = 1;
				}
				break;
			default: // shouldn't get here
				ret = 0;
			}	
		}
		
		return ret;
	}
}
