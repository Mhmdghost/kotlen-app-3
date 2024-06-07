package formallang.grammar

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

/**
 * упрощённое правило состояит только из символов (то есть не включает выражения)
 */
data class SimplifiedRule(val symbols: List<Symbol>) {
  override fun toString(): String =
    if (symbols.size> 0) symbols.fold("", { acc, e -> acc + e + " " }) else "_"
  constructor(vararg syms: Symbol) : this(syms.toList())
  /*пустое правило обчно называется эпсилон-правилом */
  fun isEpsilon(): Boolean = symbols.size == 0
}


typealias RulesMap = MutableMap<NonTerminal, ArrayList<SimplifiedRule>>

/**
 * поскольку в упрощённой грамматике нет выражений, все правила сгруппированы по левой части в списки
 */
data class SimplifiedGrammar(val rules: Map<NonTerminal, List<SimplifiedRule>>) {
  constructor(vararg theRules: Pair<NonTerminal, List<SimplifiedRule>>) :
    this(theRules.associateBy({ it.first }, { it.second }))
  override fun toString(): String =
    rules.entries.fold("", { acc, entry ->
        val rightRule = entry.value.fold("", { a, b -> "$a\n\t$b" })
        "$acc\n${entry.key} = $rightRule"
      })
  public fun toGrammar(): Grammar =
    Grammar(rules.mapValues { entry ->
      Choise(entry.value.map { Sequence(it.symbols) })
    })

  private val epsilons: Set<NonTerminal> = HashSet()
  private val firstChars: Map<SimplifiedRule, FirstCharacter> = HashMap()

  // Осторожно, простыня
  init {
    // Находим все правила и нетерминалы которые могут быть пустыми
    val eps = epsilons as MutableSet
    var changed = true
    val leftRules = rules.toMutableMap()
    while (changed) {
      changed = false
      val entryIterator = leftRules.entries.iterator()
      while(entryIterator.hasNext()) {
        val (nonTerminal, ruleList) = entryIterator.next()
        if (eps.contains(nonTerminal)) continue
        if (ruleList.any { it.symbols.isEmpty() } ||
            ruleList.any { rule ->
              rule.symbols.all { sym -> (sym is NonTerminal) && eps.contains(sym) }
            }) {
          changed = true
          eps.add(nonTerminal)
          entryIterator.remove()
        }
      }
    }
    // Находим "первые символы" для всех правил и нетерминалов
    val firstsForRules = firstChars as MutableMap
    val firstsForNonTerminals = HashMap<NonTerminal, FirstCharacter>(rules.size)
    // локальная функция выглядит ужасно, но зато можно рекурсивно её позвать 
    // кстати, ей доступны локальные переменные
    fun processNonTerminal(inProcess: MutableSet<NonTerminal>, nonTerminal: NonTerminal) {
      if (inProcess.contains(nonTerminal)) throw LeftRecursionException(nonTerminal)
      if (firstsForNonTerminals.containsKey(nonTerminal)) return
      inProcess.add(nonTerminal)
      val ruleList = rules.get(nonTerminal) ?: throw NoRuleFoundException(nonTerminal)
      var firstForNonTerminal: FirstCharacter = FirstCharWhiteList()
      for (rule in ruleList) {
        var first: FirstCharacter = FirstCharWhiteList()
        for (symbol in rule.symbols) {
          var finished = true
          when (symbol) {
            is Terminal -> first = first + symbol.getFirstChar()
            is SpecialSymbol -> first = first + symbol.getFirstChar()
            is EndOfFile -> {}
            is NonTerminal -> {
              if (!firstsForNonTerminals.containsKey(symbol))
                processNonTerminal(inProcess, symbol)
              first = first + firstsForNonTerminals.get(symbol)!!
              finished = !(epsilons.contains(symbol)) // continue if can be epsilon
            }
          }
          if (finished) break
        }
        firstsForRules.put(rule, first)
        firstForNonTerminal = firstForNonTerminal + first
      }
      firstsForNonTerminals.put(nonTerminal, firstForNonTerminal)
      inProcess.remove(nonTerminal)
    }
    val leftNonTerminals = rules.keys.toMutableSet()
    while(!leftNonTerminals.isEmpty()){
      processNonTerminal(hashSetOf(), leftNonTerminals.first())
      leftNonTerminals.removeAll(firstsForNonTerminals.keys)
    }
  }

  /**
   * Получить список возможных правил по которым можно разбирать текст начинающийся с 
   * указанного символа для указанного нетерминала
   */
  public fun getPossibleRules(nonTerminal: NonTerminal, character: Char?): List<SimplifiedRule> = (
    (rules.get(nonTerminal)?.filter {
        firstChars.get(it)?.suitable(character) ?: false
      } ?: emptyList()) + (if (epsilons.contains(nonTerminal)) listOf(SimplifiedRule()) else emptyList())
  )

  companion object {

    private class SimplifiedGrammarBuilder(
      val grammar: Grammar
    ){
      private val mutex = Mutex()
      private val targetRules = ConcurrentHashMap<NonTerminal, ArrayList<SimplifiedRule>>()

      /* получить список правил для указанного нетерминала */
      private suspend fun getOrCreateList(nonTerminal: NonTerminal):
        ArrayList<SimplifiedRule> {
          if (targetRules.containsKey(nonTerminal))
            return targetRules.get(nonTerminal)!!
          else {
            lateinit var newList: ArrayList<SimplifiedRule>  
            mutex.withLock() { 
              newList = ArrayList<SimplifiedRule>()
              targetRules.put(nonTerminal, newList)  
            }
            return newList
          }
        }

      /* добавить правило в мапу */
      private suspend fun addRule(rule: SimplifiedRule, nonTerminal: NonTerminal) =
        getOrCreateList(nonTerminal).add(rule)

      /* обработать обычное правило, превратив его в набор упрощённых */
      private suspend fun processRule(nonTerminal: NonTerminal, rule: Expression) {
        when (rule) {
          is Symbol -> addRule(SimplifiedRule(rule), nonTerminal)
          is Choise -> {
            var i = 0
            for (variant in rule.variants) {
              if (variant is Repeat || variant is Maybe) {
                val newNonTerminal = NonTerminal(nonTerminal.name + "_" + i)
                i += 1
                processRule(newNonTerminal, variant)
                addRule(SimplifiedRule(newNonTerminal), nonTerminal)
              } else processRule(nonTerminal, variant)
            }
          }
          is Repeat -> {
            processRule(nonTerminal, Sequence(rule.repeatable, nonTerminal))
            addRule(SimplifiedRule(), nonTerminal)
          }
          is Maybe -> {
            processRule(nonTerminal, rule.possible)
            addRule(SimplifiedRule(), nonTerminal)
          }
          is Sequence -> {
            val newSymbolsList: MutableList<Symbol> = ArrayList<Symbol>()
            var i = 0
            for (expr in rule.parts) {
              if (expr is Symbol)
              newSymbolsList.add(expr)
              else {
                val newNonTerminal = NonTerminal(nonTerminal.name + "_" + i)
                i += 1
                processRule(newNonTerminal, expr)
                newSymbolsList.add(newNonTerminal)
              }
            }
            addRule(SimplifiedRule(newSymbolsList), nonTerminal)
          }
        }
      }

      /**
       * Выполняет построение грамматики
       */
      public fun getGrammar(): SimplifiedGrammar {
        val jobList = ArrayList<Job>()
        for ((nonTerminal, rule) in grammar.rules) {
          jobList.add(GlobalScope.launch {
            processRule(nonTerminal, rule)
          })
        } 
        jobList.forEach{ job ->
          runBlocking{
            job.join()
          }
        }
        return SimplifiedGrammar(targetRules.toMap())
      }
    }

    /**
     * Построить упрощённую грамматику из расширенной
     */
    public fun fromGrammar(grammar: Grammar): SimplifiedGrammar =
      SimplifiedGrammarBuilder(grammar).getGrammar()
  }
}
