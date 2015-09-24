package org.ggp.base.player.gamer.statemachine.custom;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

/**
 * @author Rudi Purnomo
 * 2015/08/22
 * Stub implemented
 */
public final class MyGamer extends StateMachineGamer{

	//single player game?
	private boolean isSinglePlayerGame;

	//Only aim for highest goal for now
	private List<List<String>> highestGoalStates;
	private Integer highestGoalScore;

	//Found goal moves TODO: Delete or use this
	//private List<Move> goalMoves;

	//State history and turn number
	private Integer curStep;
	private List<String> moveHistory;

	//Time related atom
	//TODO : Fix or delete
	//private HashSet<String> timeAtoms;

	//Log
	private PrintWriter writer;

	//All the mode used
	private int depth_limit = 20; //Search depth limit
	private long buffer_time = 1500; //Buffer time
	private boolean usingHC = false; //Using hill climbing?
	private Double hCWeight = (double) 1;
	private boolean usingLocalDepthValue = true; //Using only the current depth value as comparison?
	private boolean usingAvg = false; //Using the average state distance?


	/**
	 * Using the built-in prover state machine for now
	 */
	@Override
	public StateMachine getInitialStateMachine() {
		return new ProverStateMachine();
	}

	/**
	 * Save the highest goal state distance
	 * Also reconstruct the state with unknown variables
	 */
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		//Is the game single player game
		if(getStateMachine().getRoles().size() == 1) isSinglePlayerGame = true;
		else isSinglePlayerGame = false;

		List<Integer> sortedScore = new ArrayList<Integer>(((ProverStateMachine) getStateMachine()).getGoalsMap().keySet());
		highestGoalScore = 0;
		for(Integer score : sortedScore) {
			if(highestGoalScore < score) highestGoalScore = score;
		}
		List<List<String>> highestVarGoalStates = new ArrayList<List<String>>();
		List<List<String>> highestNoVarGoalStates = new ArrayList<List<String>>();

		//Mapping possible value for gdl variables based on initial state
		Map<String, List<List<String>>> legalVariables = new HashMap<String, List<List<String>>>();
		String initialState = getStateMachine().getInitialState().toString().substring(1, getStateMachine().getInitialState().toString().length() - 1);
		String[] initialAtoms = initialState.split(", ");
		for(String initialAtom : initialAtoms) {
			String cur = removeTrueString(initialAtom);
			String[] split = cur.split(" ");
			String key = split[0];
			if(!legalVariables.containsKey(key)) legalVariables.put(key, new ArrayList<List<String>>());
			for(int i = 1; i < split.length; i++) {
				List<List<String>> curVariables = legalVariables.get(key);
				while(curVariables.size() < i) curVariables.add(new ArrayList<String>());
				//Add the legal possible values of variable
				//Note that list index 0 means first variable, index 1 means second variable, etc
				if(!curVariables.get(i-1).contains(split[i])) curVariables.get(i-1).add(split[i]);
			}
		}

		List<List<String>> highestAllGoalStates = ((ProverStateMachine) getStateMachine()).getGoalsMap().get(highestGoalScore);
		for(List<String> highestGoalState : highestAllGoalStates) {
			boolean containsVar = false;
			if(highestGoalState.toString().contains("?")) containsVar = true;
			if(!containsVar) highestNoVarGoalStates.add(highestGoalState);
			else {
				for(List<String> varGoalState : expandVariables(highestGoalState, legalVariables)) {
					highestVarGoalStates.add(varGoalState);
				}
			}
		}
		highestGoalStates = new ArrayList<List<String>>(highestNoVarGoalStates);
		highestGoalStates.addAll(highestVarGoalStates);

		System.out.println("Aiming for highest goal scores of "+highestGoalScore);
		System.out.println("There are "+highestNoVarGoalStates.size()+" goal states without variable");
		for(List<String> goals : highestNoVarGoalStates) {
			for(String goal : goals) {
				System.out.print(goal+" ");
			}
			System.out.println();
		}
		System.out.println("There are "+highestVarGoalStates.size()+" goal states with variable");
		for(List<String> goals : highestVarGoalStates) {
			for(String goal : goals) {
				System.out.print(goal+" ");
			}
			System.out.println();
		}

		//TODO: Delete or use this
		//goalMoves = new ArrayList<Move>();

		//Declaring the initial value and history
		curStep = 0;
		moveHistory = new ArrayList<String>();

		//Output preparation
		try {
			writer = new PrintWriter("text/output.txt", "UTF-8");
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			System.out.println("File for output is not found");
			e.printStackTrace();
		}
		printMode();

		//Finding out the time/step related GDL
		/* TODO: fix or delete
		timeAtoms = new HashSet<String>();
		if(isSinglePlayerGame) {
			List<MachineState> metaStates = new ArrayList<MachineState>();
			for(int i = 0; i < 3; i++) {
				metaStates.add(getStateMachine().getInitialState());
			}
			metaSimulation(metaStates);
		}*/
	}

	/**
	 * For now, let's use the same method as RandomGamer
	 * Iterative Deepening base constructed
	 */
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		long start = System.currentTimeMillis();
		boolean debug = false;

		MachineState curState = getCurrentState();
		writer.println("Current state : ");
		writer.println("  "+curState.toString());
		Role myRole = getRole();
		List<Move> moves = getStateMachine().getLegalMoves(curState, myRole);

		//Printing some stuff for debugging
		if(debug) {
			System.out.println("Current State : \n" + curState.toString());
			System.out.println("Legal Moves : ");
			for(Move move : moves) {
				System.out.println("Move : "+move.toString());
			}
			System.out.println("Current minimal distance is : "+getMinimalDistance(getCurrentState().toString()));

			Map<Move, List<MachineState>> nextStatesMap = getStateMachine().getNextStates(curState, myRole);
			for(Move move : nextStatesMap.keySet()) {
				System.out.print("Move "+move+" will result in : ");
				List<MachineState> nextStates = nextStatesMap.get(move);
				if(nextStates.size() > 1) System.out.println();
				for(MachineState state : nextStates) {
					System.out.println(state.toString());
				}
			}
			System.out.println();
		}

		Map<Move, Integer> minDistance = new HashMap<Move, Integer>();
		for(Move move : moves) {
			minDistance.put(move,Integer.MAX_VALUE);
		}

		Move selection = (moves.get(new Random().nextInt(moves.size()))); //Be random player by default

		writer.println("This is turn "+curStep);
		//If single player game, run the iterative deepening
		if(isSinglePlayerGame) selection = iterativeDeepeningSearch(curState, myRole, timeout);

		//Add the selected move to history and increase the number of step
		curStep++;
		moveHistory.add(selection.toString());

		long stop = System.currentTimeMillis();

		notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
	}

	/**
	 * 2015/08/22
	 * Do nothing when the state machine stops
	 */
	@Override
	public void stateMachineStop() {
		// nothing
		writer.close();
	}

	/**
	 * 2015/08/22
	 * Do nothing when the state machine aborts
	 */
	@Override
	public void stateMachineAbort() {
		// nothing
		writer.close();
	}

	/**
	 * 2015/08/22
	 * Still under Development, cannot be implemented
	 */
	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// nothing
	}

	/**
	 * Only returns "MyGamer" for now
	 */
	@Override
	public String getName() {
		return "MyGamer";
	}

	//-------------------------------------------------------------------------------------------------------------------------
	//			Important Helper Function
	//-------------------------------------------------------------------------------------------------------------------------

	/**
	 * Return the minimal hamming distance of current state with highest goal states
	 * TODO : Dealing with NOT
	 * @param current the current state
	 * @return the minimal distance
	 */
	private int getMinimalDistance(String currentState) {
		int minDistance = Integer.MAX_VALUE; //Maximum integer number
		String curState = currentState.substring(1, currentState.length() - 1);
		String[] curAtoms = curState.split(", ");

		for(List<String> goalState : highestGoalStates) {
			int distance = goalState.size();
			for(String goalAtom : goalState) {
				for(String curAtom : curAtoms) {
					if(curAtom.equals(goalAtom)) { //TODO: how to solve goal state with variable
						distance--;
						break;
					}
				}
			}
			if(distance < minDistance) minDistance = distance;
		}
		return minDistance;
	}

	/**
	 * Iterative Deepening Search for agent, return the best moves found
	 * @param curState the current state
	 * @param timeout the timeout time
	 * @throws TransitionDefinitionException
	 * @throws MoveDefinitionException
	 * @return best moves found
	 */
	private Move iterativeDeepeningSearch(MachineState curState, Role myRole, long timeout) throws MoveDefinitionException, TransitionDefinitionException {

		Map<Move, List<MachineState>> nextStatesMap = getStateMachine().getNextStates(curState, myRole);
		List<Move> moves = new ArrayList<Move>(nextStatesMap.keySet());
		Move selection = (moves.get(new Random().nextInt(moves.size()))); //return random if nothing is done
		Double curMinDistance = (double) getMinimalDistance(curState.toString());
		Double nextMinDistance = Double.MAX_VALUE;
		Map<Move, ArrayList<Double>> minAvgValueMoves = new HashMap<Move, ArrayList<Double>>(); //The min average distance found for doing the move
		Map<Move, List<Integer>> minValueMoves = new HashMap<Move, List<Integer>>(); //The min distance found for this move, plus the number of occurence
		HashMap<String, Integer> expandedStates = new HashMap<String, Integer>();
		Map<Move, Double> hillValue = new HashMap<Move, Double>();
		List<Move> winningMoves = new ArrayList<Move>();

		for(Move move : moves) {
			//Map of moves is linked to list with 2 elements
			ArrayList<Double> numSum = new ArrayList<Double>();
			numSum.add((double) 1); //First one is the number of values inside
			numSum.add(curMinDistance); //Second one is the sum of the values
			minAvgValueMoves.put(move, numSum);

			List<Integer> newMin = new ArrayList<Integer>();
			newMin.add(Integer.MAX_VALUE);
			newMin.add(1);
			minValueMoves.put(move, newMin);
			hillValue.put(move, hillClimb(move));
		}

		int depth = 0;
		while(depth <= depth_limit && getRemainingTime(timeout) > buffer_time && winningMoves.size() == 0) {
			writer.println("  iterative Depth "+depth);
			boolean clear = true; //Check if the search in this depth is complete
			for(Move move : nextStatesMap.keySet()) {
				depthLimitedSearch(move, nextStatesMap.get(move).get(0), myRole, 0, depth, minAvgValueMoves, minValueMoves, expandedStates, winningMoves, timeout);
				if(getRemainingTime(timeout) < buffer_time) {
					//System.out.println("run out of time!");
					clear = false;
					break;
				}
			}

			//Choose the move if clear
			if(clear) {
				//Shuffling the choice of moves, to prevent repetition when the choices have the same minimal distance
				Collections.shuffle(moves);

				Double localNextMinDistance = Double.MAX_VALUE; //The next min distance of just this depth

				//Get the moves with smallest minimal distance
				for(Move move : moves) {
					Double hill = hillValue.get(move);

					//We might use average state value or just the absolute minimum
					Double value;
					if(usingAvg) {
						Double num = minAvgValueMoves.get(move).get(0);
						Double sum = minAvgValueMoves.get(move).get(1);
						value = sum/num;
						if(usingHC) value += hill;
						writer.println("    Move : "+move.toString()+", sum: "+sum+", num: "+num+", value: "+value+", hillclimb: "+hill);
					} else {
						value = (double) minValueMoves.get(move).get(0);
						Integer occurence = minValueMoves.get(move).get(1);
						if(usingHC) value += hill;
						writer.println("    Move : "+move.toString()+", minimum: "+value+", occurence: "+occurence+", hillclimb: "+hill);
						value -= (double) occurence * 0.0001;
					}

					//Only consider the current depth? or everything?
					if(usingLocalDepthValue) {
						if(value < localNextMinDistance) {
							selection = move;
							localNextMinDistance = value;
							nextMinDistance = localNextMinDistance;
						}
					} else {
						if(value < nextMinDistance) {
							selection = move;
							nextMinDistance = value;
						}
					}
				}

				//Reset the value for the next depth
				/* TODO: Delete if unused
				for(Move move : moves) {
					ArrayList<Double> numSum = new ArrayList<Double>();
					numSum.add((double) 1);
					numSum.add(curMinDistance);
					minValueMoves.put(move, numSum);
				}*/
			}
			depth++;
		}
		expandedStates.clear();

		if(winningMoves.size() > 0) selection = winningMoves.get(new Random().nextInt(winningMoves.size()));

		//System.out.println("Selecting move "+selection.toString()+" with distance "+nextMinDistance);
		writer.println("Selecting move "+selection.toString()+" with distance "+nextMinDistance);
		writer.println();
		return selection;
	}

	/**
	 * Depth Limited Search implementation
	 * Skip the repeating state with more depth
	 * @param startingMove
	 * @param curState
	 * @param myRole
	 * @param curDepth
	 * @param depthLimit
	 * @param minAvgValueMoves
	 * @param minValueMoves
	 * @param expandedStates
	 * @param timeout
	 * @throws MoveDefinitionException
	 * @throws TransitionDefinitionException
	 */
	private void depthLimitedSearch(Move startingMove, MachineState curState, Role myRole,
			int curDepth, int depthLimit,
			Map<Move, ArrayList<Double>> minAvgValueMoves, Map<Move, List<Integer>> minValueMoves,
			HashMap<String, Integer> expandedStates,
			List<Move> winningMoves, long timeout)
					throws MoveDefinitionException, TransitionDefinitionException {
		//0 remaining depth, check the minimum distance for that move
		if(curDepth == depthLimit) {
			if(usingAvg) {
				Double minDistance = (double) getMinimalDistance(curState.toString());
				minAvgValueMoves.get(startingMove).set(0, minAvgValueMoves.get(startingMove).get(0) + 1); //one more state checked
				minAvgValueMoves.get(startingMove).set(1, minAvgValueMoves.get(startingMove).get(1) + minDistance); //add the minimal distance found
			} else {
				Integer minDistance = getMinimalDistance(curState.toString());
				Integer curMin = minValueMoves.get(startingMove).get(0);
				Integer minCount = minValueMoves.get(startingMove).get(1);
				//Update the current minimum value
				if(curMin > minDistance) {
					List<Integer> newMin = new ArrayList<Integer>();
					newMin.add(minDistance);
					newMin.add(1);
					minValueMoves.put(startingMove, newMin);
				} else if(curMin == minDistance) { //If equal, increase the occurence
					minValueMoves.get(startingMove).set(1, minCount + 1);
				}
			}
			return;
		}
		if(getStateMachine().isTerminal(curState)) {
			try {
				Integer goal = getStateMachine().getGoal(curState, myRole);
				if(goal > 0) {
					writer.println("      With Goal "+goal+" CurState is "+curState.toString());
				}
				if(goal == highestGoalScore) {
					winningMoves.add(startingMove);
				}
			} catch (GoalDefinitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		Map<Move, List<MachineState>> nextStatesMap = getStateMachine().getNextStates(curState, myRole);
		for(Move move : nextStatesMap.keySet()) {
			if(getRemainingTime(timeout) < buffer_time) break; //Do not expand if less than 3 seconds remaining
			MachineState thisState = nextStatesMap.get(move).get(0);
			String stateString = getNonStepAtoms(thisState);

			//Update the minimal distance to state
			//TODO : Make this works
			if(!expandedStates.containsKey(stateString) ||
					(expandedStates.containsKey(stateString) && expandedStates.get(stateString) > curDepth)) {
				expandedStates.put(stateString, curDepth);
			} else if(expandedStates.get(stateString) < curDepth) {
				continue;
			}
			depthLimitedSearch(startingMove, thisState, myRole, curDepth + 1, depthLimit, minAvgValueMoves, minValueMoves, expandedStates, winningMoves, timeout);
		}
	}

	/**
	 * Basic hill climbing
	 * @param move
	 * @return
	 */
	private Double hillClimb(Move move) {
		Double sum = (double) 0;
		for(int i = 0; i < moveHistory.size(); i++) {
			if(move.toString().equals(moveHistory.get(i))) {
				sum += (double) hCWeight/((double)curStep - (double)i);
			}
		}
		return sum;
	}

	/**
	 * Get the combined sorted string of state atom that is not related to time/step
	 * TODO : Proper way to remove time/step atom
	 * @param state
	 * @return
	 */
	private String getNonStepAtoms(MachineState state) {
		String ret = "";
		List<GdlSentence> stateAtoms = new ArrayList<GdlSentence>(state.getContents());
		for(GdlSentence atom : stateAtoms) {
			String atomStr = atom.toString();
			if(atomStr.contains("step")) continue;
			ret += atomStr;
		}
		return ret;
	}

	/**
	 * TODO : Fix or delete
	 * @param metaStates
	 */
	@SuppressWarnings("unused")
	private void metaSimulation(List<MachineState> metaStates) {
		LinkedList<String> notTerminated = new LinkedList<String>();
		for(int i = 0; i < metaStates.size(); i++) {
			notTerminated.add(Integer.toString(i));
		}
		while(notTerminated.size() > 0) {
			int i = 0;
			while(i < notTerminated.size()) {
				Integer curIndex = Integer.parseInt(notTerminated.get(i));
				MachineState curState = metaStates.get(curIndex);
				if(getStateMachine().isTerminal(curState)) {
					notTerminated.remove(notTerminated.get(i));
				} else {
					i++;
				}
				List<MachineState> nextStates;
				try {
					nextStates = getStateMachine().getNextStates(curState);
					metaStates.set(curIndex, nextStates.get(new Random().nextInt(nextStates.size())));
				} catch (MoveDefinitionException | TransitionDefinitionException e) {
					e.printStackTrace();
				}
			}
			//put the atom that has same value throughout
			for(int j = 0; j < metaStates.size(); j++) {

			}
		}
	}

	//-------------------------------------------------------------------------------------------------------------------------
	//			Helper Function
	//-------------------------------------------------------------------------------------------------------------------------
	/**
	 * Removing the bracket and true statement of atom
	 * TODO : Better Implementation
	 * @param str the original string
	 * @return edited string
	 */
	private String removeTrueString(String str) {
		if(!str.contains("true")) return str;
		String cur = str.substring(1, str.length() - 1); //Removing the outside bracket
		cur = cur.trim();
		if(cur.contains("(") && cur.contains("(")) {
			//Remove the "true ( " and ")"
			cur = cur.substring(7, cur.length() - 1);
		} else {
			cur = cur.split(" ")[1]; //If it has no bracket, just take the next value after 'true'
		}
		return cur;
	}

	private List<List<String>> expandVariables(List<String> varGoalState, Map<String, List<List<String>>> legalVariables) {
		List<List<String>> ret = new ArrayList<List<String>>();
		Map<String, List<String>> qVariables = new HashMap<String, List<String>>();
		Map<String, List<String>> qVariablesTrans = new HashMap<String, List<String>>();

		/*
		 * Get the relation of the variable, i.e.
		 * ?m means first variable of cell and second variable of row --> ?m = [cell 1, row 2]
		 */
		for(String varAtom : varGoalState) {
			String[] noTrueVariables = removeTrueString(varAtom).split(" ");
			String gdlKey = noTrueVariables[0];
			for(int i = 1; i < noTrueVariables.length; i++) {
				if(noTrueVariables[i].contains("?")) {
					if(!qVariables.containsKey(noTrueVariables[i])) qVariables.put(noTrueVariables[i], new ArrayList<String>());
					List<String> varIndex = qVariables.get(noTrueVariables[i]);
					if(!varIndex.contains(gdlKey+" "+i)) varIndex.add(gdlKey+" "+i);
				}
			}
		}

		/*
		 * Now translate them based on the legalVariables value list
		 * Compute all the legal value for every ? variable
		 */
		for(String keyVar : qVariables.keySet()) {
			List<String> legalValues = new ArrayList<String>();
			boolean initiated = false;
			for(String relation : qVariables.get(keyVar)) {
				if(initiated && legalValues.size() == 0) break;
				String gdlKey = relation.split(" ")[0];
				int index = Integer.parseInt(relation.split(" ")[1]);
				List<String> curLegalValues = new ArrayList<String>();
				if(legalVariables.containsKey(gdlKey)) curLegalValues = legalVariables.get(gdlKey).get(index - 1);
				if(!initiated) {
					legalValues = new ArrayList<String>(curLegalValues);
					initiated = true;
				} else {
					legalValues.retainAll(curLegalValues);
				}
			}
			qVariablesTrans.put(keyVar, legalValues);
		}

		/*
		 * Now replace the ? variable in the goal state with all the legal value
		 */
		replaceRecursive(varGoalState, 0, qVariablesTrans, ret);

		//ret.add(varGoalState);
		return ret;
	}

	/**
	 * Replace the "?" variable in goal state string with all possible combinations of legal state
	 * @param cur the currently translated goal state
	 * @param curVarIndex current index of variable to be replaced
	 * @param qVariablesTrans legal value for "?" variable
	 * @param ret the return list
	 */
	private void replaceRecursive(List<String> cur, int curVarIndex, Map<String, List<String>> qVariablesTrans, List<List<String>> ret) {
		if(curVarIndex >= qVariablesTrans.keySet().size()) {
			ret.add(cur);
			return;
		}
		List<String> keyList = new ArrayList<String>(qVariablesTrans.keySet());
		String replaced = keyList.get(curVarIndex);
		List<String> replacements = qVariablesTrans.get(replaced);
		for(String replacement : replacements) {
			List<String> dummyCurs = new ArrayList<String>(cur);
			for(int i = 0; i < dummyCurs.size(); i++) {
				dummyCurs.set(i, cur.get(i).replace(replaced, replacement));
			}
			replaceRecursive(dummyCurs, curVarIndex+1, qVariablesTrans, ret);
		}
	}

	/**
	 * Get the remaining time based on timeout clock
	 * @param timeout timeout clock
	 * @return the remaining time
	 */
	private long getRemainingTime(long timeout) {
		return timeout - System.currentTimeMillis();
	}

	private void printMode() {
		writer.println("Setting up for MyGamer");
		writer.println("Using depth limit of "+depth_limit+" with buffer time "+buffer_time+"ms");

		if(usingHC) {
			writer.println("Using basic Hill Climbing");
		} else {
			writer.println("Not using Hill Climbing");
		}

		if(usingLocalDepthValue) {
			writer.println("Using local depth value as comparison");
		} else {
			writer.println("Using global depth value as comparison");
		}

		if(usingAvg) {
			writer.println("Using average value of all expanded states");
		} else {
			writer.println("Only using the absolute minimum value");
		}
	}

}
