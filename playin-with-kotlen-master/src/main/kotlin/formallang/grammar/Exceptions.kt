package formallang.grammar

/**
 * Выбрасывается при попытке создания пустого терминала. 
 */
class WrongTerminalException() : 
  Exception("Empty terminals are not allowed!")

/**
 * Выбрасывается при попытке найти правила, соответствующие заданному нетерминалу,
 * но при этом в данной грамматике таких правил нет.
 */
class NoRuleFoundException(nonTerminal : NonTerminal):
  Exception("No rule found for non-terminal called $nonTerminal")

/**
 * Выбрасывается во время анализа грамматики на предмет наличия левой рекурсии.  
 * Правила с левой рекурсией должны быть преобразованы, парсер, работающий 
 * слева-направо, сверху-вниз не может разбирать тексты с гграмматикой где есть левая рекурсия
 */  
class LeftRecursionException(nonTerminal : NonTerminal):
  Exception("Left recursion found when checking nonTerminal: $nonTerminal")