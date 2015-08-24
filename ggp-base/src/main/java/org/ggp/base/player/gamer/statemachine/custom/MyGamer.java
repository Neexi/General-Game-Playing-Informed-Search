package org.ggp.base.player.gamer.statemachine.custom;

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

	/**
	 * 2015/08/22
	 * Using the built-in prover state machine for now
	 */
	@Override
	public StateMachine getInitialStateMachine() {
		return new ProverStateMachine();
	}

	/**
	 * 2015/08/22
	 * Do nothing at the beginning of the match
	 */
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// nothing

	}

	/**
	 * 2015/08/22
	 * For now, let's use the same method as RandomGamer
	 */
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		long start = System.currentTimeMillis();
		//Printing some stuff
		System.out.println("Current State : \n" + getCurrentState().toString());

		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		System.out.println("Legal Moves : ");
		for(Move move : moves) {
			System.out.println(move.toString());
		}
		System.out.println("Terminals : ");
		List<String> goalStates = ((ProverStateMachine) getStateMachine()).getTerminals();
		for(String goalState : goalStates) {
			System.out.println(goalState);
		}
		System.out.println();

		//String terminal = ((ProverStateMachine)getStateMachine()).getTerminal(getCurrentState()).toString();
		//System.out.println("Terminal : " + terminal);

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

}
