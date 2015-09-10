package org.ggp.base.player.gamer.statemachine.custom;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.Move;
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

	//Only aim for highest goal for now
	private List<List<String>> highestGoalStates;
	private List<List<String>> highestVarGoalStates;
	private Integer highestGoalScore;

	/**
	 * 2015/08/22
	 * Using the built-in prover state machine for now
	 */
	@Override
	public StateMachine getInitialStateMachine() {
		return new ProverStateMachine();
	}

	/**
	 * Save the highest goal state distance
	 */
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// nothing
		List<Integer> sortedScore = new ArrayList<Integer>(((ProverStateMachine) getStateMachine()).getGoalsMap().keySet());
		highestGoalScore = sortedScore.get(sortedScore.size() - 1);
		highestVarGoalStates = new ArrayList<List<String>>();
		highestGoalStates = new ArrayList<List<String>>();
		List<List<String>> highestAllGoalStates = ((ProverStateMachine) getStateMachine()).getGoalsMap().get(highestGoalScore);
		for(List<String> highestGoalState : highestAllGoalStates) {
			boolean containsVar = false;
			for(String goalAtom : highestGoalState) {
				if(goalAtom.contains("?")) {
					highestVarGoalStates.add(highestGoalState);
					containsVar = true;
					break;
				}
			}
			if(!containsVar) highestGoalStates.add(highestGoalState);
		}
		System.out.println("Aiming for highest goal scores of "+highestGoalScore);
		System.out.println("There are "+highestGoalStates.size()+" goal states without variable");
		for(List<String> goals : highestGoalStates) {
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
	}

	/**
	 * 2015/08/22
	 * For now, let's use the same method as RandomGamer
	 */
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		long start = System.currentTimeMillis();
		//Printing some stuff for debugging
		System.out.println("Current State : \n" + getCurrentState().toString());
		getMinimalDistance(getCurrentState().toString());

		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		System.out.println("Legal Moves : ");
		for(Move move : moves) {
			System.out.println(move.toString());
		}
		/*
		System.out.println("Goals : ");
		List<String> terminals = ((ProverStateMachine) getStateMachine()).getGoals();
		for(String terminal : terminals) {
			System.out.println(terminal);
		}
		System.out.println("Goals : ");
		Map<Integer, List<List<String>>> goalsMap = ((ProverStateMachine) getStateMachine()).getGoalsMap();
		for(Integer score : goalsMap.keySet()) {
			System.out.println("Goal for score "+score+" : ");
			List<List<String>> goalsList = goalsMap.get(score);
			for(List<String> goals : goalsList) {
				for(String goal : goals) {
					System.out.print(goal+" ");
				}
				System.out.println();
			}
		}*/
		System.out.println("Current minimal distance is : "+getMinimalDistance(getCurrentState().toString()));
		System.out.println();


		Move selection = (moves.get(new Random().nextInt(moves.size())));

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
	}

	/**
	 * 2015/08/22
	 * Do nothing when the state machine aborts
	 */
	@Override
	public void stateMachineAbort() {
		// nothing
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
	 * 2015/08/22
	 * Only returns "MyGamer" for now
	 */
	@Override
	public String getName() {
		return "MyGamer";
	}

	/**
	 * Return the minimal hamming distance of current state with highest goal states
	 * @param current the current state
	 * @return the minimal distance
	 */
	private int getMinimalDistance(String currentState) {
		int minDistance = 2147483647; //Maximum integer number
		String curState = currentState.substring(1, currentState.length() - 1);
		String[] curAtoms = curState.split(", ");

		//Handle the one without variable
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

		/*
		//TODO : Handle the one with variable
		for(List<String> goalState : highestVarGoalStates) {
			int distance = goalState.size();
			int minNoMatch = goalState.size();
		}*/

		return minDistance;
	}

	/*
	private boolean matchVariable(String goalStr, String cur) {
		String goal = goalStr.replaceAll("\\?[^ ]+","[^ ]+");
		goal = goal.replace("(", "\\(");
		goal = goal.replace(")", "\\)");
		if(cur.matches(goal)) {
			//System.out.println(cur+" matchs with "+goal);
			return true;
		}
		//System.out.println(cur+" does not match with "+goal);
		return false;
	}*/

	/*
	private List<String> getMatchingVariables(List<String> goalState, List<String> currentState) {

	}*/

}
