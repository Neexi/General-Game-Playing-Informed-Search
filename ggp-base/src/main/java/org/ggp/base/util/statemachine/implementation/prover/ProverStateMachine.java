package org.ggp.base.util.statemachine.implementation.prover;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.prover.Prover;
import org.ggp.base.util.prover.aima.AimaProver;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;
import org.ggp.base.util.statemachine.implementation.prover.result.ProverResultParser;

import com.google.common.collect.ImmutableList;


public class ProverStateMachine extends StateMachine
{
	private MachineState initialState;
	private Prover prover;
	private ImmutableList<Role> roles;
	//Custom
	private List<String> specialKeys = Arrays.asList("distinct","does", "goal","init","legal","next","role","terminal","true","not","<=");
	private List<String> specialTerminals = Arrays.asList("terminal","<=");
	private List<String> initialKeys;
	//private Map<String,List<String>> translatedTerminals;
	private List<String> terminals;

	/**
	 * Initialize must be called before using the StateMachine
	 */
	public ProverStateMachine()
	{

	}

	@Override
	public void initialize(List<Gdl> description)
	{
		prover = new AimaProver(description);
		roles = ImmutableList.copyOf(Role.computeRoles(description));
		initialState = computeInitialState();
		initialKeys = computeInitialKeys();
		//translatedTerminals = new HashMap<String,List<String>>();
		terminals = ImmutableList.copyOf(computeTerminals(description));
	}

	private MachineState computeInitialState()
	{
		Set<GdlSentence> results = prover.askAll(ProverQueryBuilder.getInitQuery(), new HashSet<GdlSentence>());
		return new ProverResultParser().toState(results);
	}

	@Override
	public int getGoal(MachineState state, Role role) throws GoalDefinitionException
	{
		Set<GdlSentence> results = prover.askAll(ProverQueryBuilder.getGoalQuery(role), ProverQueryBuilder.getContext(state));

		if (results.size() != 1)
		{
		    GamerLogger.logError("StateMachine", "Got goal results of size: " + results.size() + " when expecting size one.");
			throw new GoalDefinitionException(state, role);
		}

		try
		{
			GdlRelation relation = (GdlRelation) results.iterator().next();
			GdlConstant constant = (GdlConstant) relation.get(1);

			return Integer.parseInt(constant.toString());
		}
		catch (Exception e)
		{
			throw new GoalDefinitionException(state, role);
		}
	}

	@Override
	public MachineState getInitialState()
	{
		return initialState;
	}

	@Override
	public List<Move> getLegalMoves(MachineState state, Role role) throws MoveDefinitionException
	{
		Set<GdlSentence> results = prover.askAll(ProverQueryBuilder.getLegalQuery(role), ProverQueryBuilder.getContext(state));

		if (results.size() == 0)
		{
			throw new MoveDefinitionException(state, role);
		}

		return new ProverResultParser().toMoves(results);
	}

	@Override
	public MachineState getNextState(MachineState state, List<Move> moves) throws TransitionDefinitionException
	{
		Set<GdlSentence> results = prover.askAll(ProverQueryBuilder.getNextQuery(), ProverQueryBuilder.getContext(state, getRoles(), moves));

		for (GdlSentence sentence : results)
		{
			if (!sentence.isGround())
			{
				throw new TransitionDefinitionException(state, moves);
			}
		}

		return new ProverResultParser().toState(results);
	}

	@Override
	public List<Role> getRoles()
	{
		return roles;
	}

	@Override
	public boolean isTerminal(MachineState state)
	{
		return prover.prove(ProverQueryBuilder.getTerminalQuery(), ProverQueryBuilder.getContext(state));
	}

	//Custom functions

	/**
	 * Debugging
	 * @return
	 */
	public int returnOne() {
		return 1;
	}

	//TODO : Improve this later so that initial keys are not cluttered
	private List<String> computeInitialKeys() {
		String initial = initialState.toString();
		List<String> initialKeys = new ArrayList<String>();
		String[] initialWords = initial.split(" ");
		for(String word : initialWords) {
			if(word.replaceAll("[^a-z0-9A-Z]", "").length() > 0 && !specialKeys.contains(word)) {
				String dummy = word;
				dummy = dummy.replaceAll("[^a-z0-9A-Z]", "");
				if(!initialKeys.contains(dummy)) {
					initialKeys.add(dummy);
				}
			}
		}
		return initialKeys;
	}

	private List<String> computeTerminals(List<? extends Gdl> description) {
	   List<String> terminals = new ArrayList<String>();
	   List<String> terminalKeys = new ArrayList<String>();
	   int i = 0;
	   while(i < description.size()) {
           String str = description.get(i).toString();
           if (str.contains("terminal")) {
        	   String terminalResult = "(<= terminal ";
        	   str = removeOutBracket(str);
               for(String special : specialTerminals) {
            	   str = str.replace(special, "");
               }
               str = str.trim();
               terminalKeys = splitOutBracket(str);
               for(String key : terminalKeys) {
            	   //System.out.println("Key is "+key);
            	   terminalResult += translateRecursive(description, key);
               }
               terminalResult += ")";
               terminals.add(terminalResult);
           }
    	   i++;
	   }
	   return terminals;
	}

	public List<String> getTerminals() {
		return terminals;
	}

	private String translateRecursive(List<? extends Gdl> description, String str) {
		String dummy = removeOutBracket(str).trim();
		String ret = "";
		List<String> terminals = new ArrayList<String>();
		if(!dummy.contains("(") && !dummy.contains(")")) {
			terminals.add(dummy);
		} else {
			terminals = splitOutBracket(dummy);
		}

		for(String terminal : terminals) {
			if(!terminal.contains("(") && !terminal.contains(")")) {
				List<String> elements = Arrays.asList(terminal.split(" "));
				int i = 0;
				while(i < elements.size() && specialKeys.contains(elements.get(i))) {
					ret += elements.get(i);
					i++;
				}
				if(i < elements.size() && !specialKeys.contains(elements.get(i))) {
					String key = elements.get(i);
					//System.out.println("Key is "+key);
					List<String> variables = new ArrayList<String>();
					for(int j = i+1; j < elements.size(); j++) {
						variables.add(elements.get(j));
					}
					//Only translate key that has not been translated and is not in initial state
					if(!initialKeys.contains(key) && !specialKeys.contains(key)) {
						ret += translate(description, key, variables);
					}
				}
			} else {
				ret += translateRecursive(description, terminal);
			}
		}
		//ret += ")";
		return ret;
	}

	private String translate(List<? extends Gdl> description, String key, List<String> variables) {
		String result = "";
		for(Gdl gdl : description) {
			String str = gdl.toString();
			//if(str.contains(key)) System.out.println("Found "+str);
			//TODO : Might change this later
			//System.out.println("key is "+key);
			if(str.matches("^\\s*\\(\\s*\\<\\=\\s*"+key+".*") || str.matches("^\\s*\\(\\s*\\<\\=\\s*\\(\\s*"+key+".*")) { //Find the GDL with description of the key
				str = removeOutBracket(str);
				str = str.replace("<=", "");
				str = str.trim();
				List<String> split = splitOutBracket(str);
				for(int i = 1; i < split.size(); i++) {
					result += split.get(i);
				}
			}
		}
		//result += ")";
		return result;
	}

	private String removeOutBracket(String s) {
		String output = s;
        if(output.contains("(") && output.contains(")")) {
	        int firstBracket = output.indexOf("(");
	        int lastBracket = output.lastIndexOf(")");
	        output = output.substring(firstBracket+1, lastBracket);
	        output = output.trim();
        }
        return output;
	}

	private List<String> splitOutBracket(String s){
	    List<String> l = new LinkedList<String>();
	    int depth=0;
	    StringBuilder sb = new StringBuilder();
	    for(int i=0; i<s.length(); i++){
	        char c = s.charAt(i);
	        if(c=='('){
	            depth++;
	        }else if(c==')'){
	            depth--;
	        }else if(c==' ' && depth==0){
	            l.add(sb.toString());
	            sb = new StringBuilder();
	            continue;
	        }
	        sb.append(c);
	    }
	    l.add(sb.toString());
	    return l;
	}
	/*
	private boolean isTabulated(String str) {
		char ch = str.charAt(0);
		if((str.trim().length() > 0) && (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n')) {
			return true;
		}
		return false;
	}*/

	/*
	private List<String> computeTerminals() {
		Set<GdlSentence> results = prover.askAll(ProverQueryBuilder.getTerminalQuery2(), new HashSet<GdlSentence>());
		List<String> goalStates = new ArrayList<String>();
		for (GdlSentence sentence : results)
		{
			goalStates.add(sentence.toString());
		}
		return goalStates;
	}*/

	/*
	private List<String> computeTerminals(List<? extends Gdl> description) {
	   List<String> terminals = new ArrayList<String>();
	    for (Gdl gdl : description) {
	        if (gdl instanceof GdlRelation) {
	            GdlRelation relation = (GdlRelation) gdl;
	            if (relation.getName().getValue().equals("terminal")) {
	            	System.out.println("success");
	                terminals.add(relation.toString());
	            }
	        }
	    }
	    return terminals;
	}*/

	   /* from above
	   String result = str;
	   while(i+1 < description.size() && isTabulated(description.get(i+1).toString())) {
		   i++;
		   str = description.get(i).toString();
		   result += str;
	   }*/

}