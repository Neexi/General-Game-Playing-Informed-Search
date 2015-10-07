package org.ggp.base.util.statemachine.implementation.prover;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
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

//TODO : A lot of bugfixes
public class ProverStateMachine extends StateMachine
{

	private boolean translationEnabled = false;
	private MachineState initialState;
	private Prover prover;
	private ImmutableList<Role> roles;
	//Custom
	private List<String> specialKeys = Arrays.asList("distinct","does", "goal","init","legal","next","role","terminal","true","not","<=");
	private List<String> specialTerminals = Arrays.asList("terminal","<=");
	private List<String> initialKeys;
	private Map<String,List<String>> translatedKeys;
	private List<String> terminals;
	private List<String> goals;
	private Map<Integer, List<List<String>>> goalsMap;

	/**
	 * Initialize must be called before using the StateMachine
	 */
	public ProverStateMachine()
	{

	}

	@Override
	public void initialize(List<Gdl> description)
	{
		System.out.println();
		prover = new AimaProver(description);
		roles = ImmutableList.copyOf(Role.computeRoles(description));
		initialState = computeInitialState();
		initialKeys = computeInitialKeys();
		translatedKeys = new HashMap<String,List<String>>();
		terminals = new ArrayList<String>();
		goals = new ArrayList<String>();
		goalsMap = new HashMap<Integer, List<List<String>>>();
		if(translationEnabled) {
			computeTerminals(description);
			computeGoals(description);
			computeGoalsMap();
		}
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

	/**
	 * Finding the translations of GDL keyword in terminal state and applying them
	 * @param description GDL description
	 */
	private void computeTerminals(List<? extends Gdl> description) {
	   //List<String> terminals = new ArrayList<String>();
	   List<String> terminalKeys = new ArrayList<String>();
	   int i = 0;

	   //Finding all the keys translation
	   while(i < description.size()) {
           String str = description.get(i).toString();
           if (str.contains("terminal")) {
        	   addTerminals(str);
        	   str = removeOutBracket(str);
               for(String special : specialTerminals) {
            	   str = str.replace(special, "");
               }
               str = str.trim();
               terminalKeys = splitOutBracket(str);
               for(String key : terminalKeys) {
            	   constructRecursive(description, key);
               }
           }
    	   i++;
	   }

	   applyTranslation(0); //Apply the translation to the base terminal
	}

	/**
	 * Return the terminal state
	 * @return
	 */
	public List<String> getTerminals() {
		return terminals;
	}


	/**
	 * Adding a string to list of terminals
	 * @param str
	 */
	private void addTerminals(String str) {
		terminals.add(str);
	}

	/**
	 * Compute goal state
	 * @param description
	 */
	private void computeGoals(List<? extends Gdl> description) {
		   List<String> goalKeys = new ArrayList<String>();
		   int i = 0;

		   //Finding all the keys translation
		   while(i < description.size()) {
	           String str = description.get(i).toString();
	           if (str.contains("goal")) {
	        	   addGoals(str);
	        	   str = removeOutBracket(str);
	               for(String special : specialTerminals) {
	            	   str = str.replace(special, "");
	               }
	               str = str.trim();
	               goalKeys = splitOutBracket(str);
	               int j = 0;
	               for(String key : goalKeys) {
	            	   if(j == 0) continue;
	            	   constructRecursive(description, key);
	            	   j++;
	               }
	           }
	    	   i++;
		   }

		   applyTranslation(1); //Apply the translation to the base terminal
	}

	/**
	 * get list of goal state string
	 * @return list of goal state string
	 */
	public List<String> getGoals() {
		return goals;
	}

	/**
	 * Add a string to goal state list
	 * @param str the string
	 */
	private void addGoals(String str) {
		goals.add(str);
	}

	/**
	 * Construct a map of score to state
	 */
	private void computeGoalsMap() {
		for(String goal : goals) {
			goal = removeOutBracket(goal);
			List<String> goalKey = splitOutBracket(goal);
			List<String> mapKey = new ArrayList<String>();
			String scoreStr = splitOutBracket(removeOutBracket(goalKey.get(1))).get(2);
			Integer score;

			if(scoreStr.matches("\\d+")) score = Integer.parseInt(scoreStr);
			else score = 0;
			for(int i = 2; i < goalKey.size(); i++) {
				mapKey.add(goalKey.get(i));
			}

			//Put the information to the hashmap
			if(goalsMap.containsKey(score)) {
				goalsMap.get(score).add(mapKey);
			} else { //this score is never recorded
				List<List<String>> lists = new ArrayList<List<String>>();
				lists.add(mapKey);
				goalsMap.put(score, lists);
			}
		}
	}

	public Map<Integer, List<List<String>>> getGoalsMap() {
		return goalsMap;
	}

	/**
	 * Recursive function for construction of keyword to be translated
	 * @param description GDL keyword
	 * @param str current string to be translated
	 */
	private void constructRecursive(List<? extends Gdl> description, String str) {
		String dummy = removeOutBracket(str).trim();
		List<String> terminals = new ArrayList<String>();
		if(!dummy.contains("(") && !dummy.contains(")")) {
			terminals.add(dummy);
		} else {
			terminals = splitOutBracket(dummy);
		}

		for(String terminal : terminals) {
			if(!terminal.contains("(") && !terminal.contains(")")) {
				//List<String> elements = Arrays.asList(terminal.split(" "));
				List<String> elements = splitOutBracket(terminal);
				int i = 0;
				while(i < elements.size() && specialKeys.contains(elements.get(i))) {
					i++;
				}
				if(i < elements.size() && !specialKeys.contains(elements.get(i))) {
					String key = elements.get(i);
					String fullKey;
					List<String> variables = new ArrayList<String>();
					fullKey = key;
					for(int j = i+1; j < elements.size(); j++) {
						fullKey = fullKey + " " + elements.get(j);
						variables.add(elements.get(j));
					}
					//Only translate key that has not been translated and is not in initial state
					if(!translatedKeys.containsKey(fullKey) && !initialKeys.contains(key) && !specialKeys.contains(key)) {
						if(!key.contains("(") && !key.contains(")")) {
							translate(description, fullKey, key, variables);
						} else {
							constructRecursive(description, fullKey);
						}
					}
				}
			} else {
				constructRecursive(description, terminal);
			}
		}
	}

	/**
	 * Finding the translation of specific key in GDL description, only works for two level? still buggy
	 * TODO : Improve variable translation and more than two level translation
	 * @param description GDL description
	 * @param key the primary key
	 * @param fullStr topmost key with the variables
	 */
	private void translate(List<? extends Gdl> description, String fullStr, String key, List<String> variables) {
		for(Gdl gdl : description) {
			String str = gdl.toString();
			//Find gdl descriptions which translates the key
			if(str.matches("^\\s*\\(\\s*\\<\\=\\s*"+key+".*") || str.matches("^\\s*\\(\\s*\\<\\=\\s*\\(\\s*"+key+".*")) { //Find the GDL with description of the key
				List<String> translations = new ArrayList<String>();
				translations.add("");
				str = removeOutBracket(str);
				str = str.replace("<=", "");
				str = str.trim();

				str = replaceVariable(str, splitOutBracket(str).get(0), variables);
				List<String> splits = splitOutBracket(str);

				//Recursively translate needed key inside translation
				for(int i = 1; i < splits.size(); i++) {
					if(i > 1) {
						for (final ListIterator<String> translation = translations.listIterator(); translation.hasNext();) {
							final String element = translation.next();
							translation.set(element + " ");
						}
					}
					String split = splits.get(i);
					split = removeOutBracket(split);
					//List<String> splitKeys = Arrays.asList(split.split(" "));
					List<String> splitKeys = splitOutBracket(split);
					List<String> splitVariables = new ArrayList<String>();

					//TODO : Better implementation
					//To check special keys
					int j = 0;
					while(j < splitKeys.size() && specialKeys.contains(splitKeys.get(j))) {
						j++;
					}
					if(j < splitKeys.size() && !specialKeys.contains(splitKeys.get(j))) {
						String splitKey = splitKeys.get(j);
						if(splitKey.contains("(") && splitKey.contains(")")) {
							split = removeOutBracket(splitKey);
							List<String> splitSplitKeys = splitOutBracket(split);
							splitKey = splitSplitKeys.get(0);
							for(int k = 1; k < splitSplitKeys.size(); k++) {
								splitVariables.add(splitSplitKeys.get(k));
							}
						} else {
							for(int k = j+1; k < splitKeys.size(); k++) {
								splitVariables.add(splitKeys.get(k));
							}
						}
						//System.out.println("Translating : "+splitKey+", with full string : "+split);
						translate(description, split, splitKey, splitVariables);
					}
					/*
					String splitKey = splitKeys.get(0);
					for(int k = 1; k < splitKeys.size(); k++) {
						splitVariables.add(splitKeys.get(k));
					}*/



					//Containing something that was successfully translated, translate them first
					if(translatedKeys.containsKey(split)) {
						List<String> translationsDummy = new ArrayList<String>(translations);
						translations.clear();
						for(String translation : translationsDummy) {
							for(String translated : translatedKeys.get(split)) {
								translations.add(translation+" "+translated);
							}
						}
						translatedKeys.remove(split); //Second level translation is only used once
					} else {
						for (final ListIterator<String> translation = translations.listIterator(); translation.hasNext();) {
							final String element = translation.next();
							translation.set(element + splits.get(i));
						}
					}
				}
				for(String translation : translations) {
					addTranslation(fullStr, translation.trim());
				}
			}
		}
	}

	/**
	 * Replace variable in a string with the input variable
	 * @param str string
	 * @param keyString variable part of string
	 * @param variables input variable
	 * @return replaced string
	 */
	private String replaceVariable(String str, String keyString, List<String> variables) {
		if(variables.size() == 0) {
			return str;
		}
		String ret = str;
		//List<String> keyStrings = Arrays.asList(removeOutBracket(keyString).trim().split(" "));
		List<String> keyStrings = splitOutBracket(removeOutBracket(keyString).trim());
		for(int i = 1; i < keyStrings.size(); i++) {
			if(i-1 < variables.size()) ret = ret.replace(keyStrings.get(i), variables.get(i-1));
		}
		return ret;
	}

	/*
	private void translateRecursive(List<? extends Gdl> description, String trans, String cur) {
		List<String> elements = Arrays.asList(cur.split(" "));
		int i = 0;
		while(i < elements.size() && specialKeys.contains(elements.get(i))) {
			i++;
		}
		if(i < elements.size() && !specialKeys.contains(elements.get(i))) {
			String key = elements.get(i);
		}
	}*/

	/**
	 * Adding a translation of GDL key
	 * @param key the key to be translated
	 * @param trans the translation
	 */
	private void addTranslation(String key, String trans) {
		List<String> transList = new ArrayList<String>();
		if(!translatedKeys.containsKey(key)) {
			transList.add(trans);
			translatedKeys.put(key, transList);
		} else {
			transList = translatedKeys.get(key);
			transList.add(trans);
			translatedKeys.put(key, transList);
		}
	}

	/**
	 * Applying the translation of GDL keys to base terminal state
	 * @param mode 0 is terminal, 1 is goal
	 */
	private void applyTranslation(int mode) {
		List<String> dummies;
		if(mode == 0){
			dummies = terminals;
		} else {
			dummies = goals;
		}
		Set<String> translatedKeysSet = translatedKeys.keySet();
		if(mode == 0) terminals = new ArrayList<String>();
		else goals = new ArrayList<String>();
		//System.out.println("Size is : "+terminalsDummy.size());
		for(String dummy : dummies) {
			List<String> keys = new ArrayList<String>();
			for(String translatedKey : translatedKeysSet) {
				if(dummy.contains(translatedKey)) {
					keys.add(translatedKey);
				}
			}
			applyTranslationRecursive(dummy, keys, 0, keys.size(), mode);

		}
		//System.out.println("Size is : "+terminals.size());
		//terminals = translated;
	}

	/**
	 * Recursive function for >1 possibilities of translation
	 * e.g. in tic tac toe --> line ?x can means "row ?m ?x", "column ?m ?x", or "diagonal ?x"
	 * @param curTerminal current terminal (can be semi translated)
	 * @param keys current keys to be translated
	 * @param curKey current key index
	 * @param keySize size of keys for this terminal
	 */
	private void applyTranslationRecursive(String curTerminal, List<String> keys, int curKey,  int keySize, int mode) {
		if(curKey < keySize) { //There is something to translate, translate all possibilities
			String curKeyString = keys.get(curKey);
			List<String> translations = translatedKeys.get(curKeyString);
			for(String translation : translations) {
				String dummy = "";
				if(curKeyString.split("\\s+").length > 1) {
					dummy = curTerminal.replace("( "+curKeyString+" )",translation);
				} else {
					dummy = curTerminal.replace(curKeyString, translation);
				}
				applyTranslationRecursive(dummy, keys, curKey+1, keySize, mode);
			}
		} else { //Nothing else to apply translation
			//TODO : Crude method of removing double space, might need to change this later
			if(mode == 0) addTerminals(curTerminal.trim().replaceAll(" +", " "));
			else addGoals(curTerminal.trim().replaceAll(" +", " "));
		}
	}

	/**
	 * Helper function, removing outer bracket of a string
	 * @param s
	 * @return
	 */
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

	/**
	 * Helper function, splitting string by the outermost bracket
	 * e.g. "(a) (b) (c (d))" becomes "a", "b", and "(c (d))"
	 * @param s
	 * @return
	 */
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

}