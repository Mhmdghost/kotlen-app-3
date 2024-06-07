package formallang.grammar

/**
 * позволяет комбинировать различные выражения
 */
sealed class Expression

/**
 * символ - основной элемент грамматики. может быть 
 * терминалом (@see Terminal) и нетерминалом (@see NonTerminal).
 * специальный символ (@see SpecialSymbol)- аналог терминала, полезен во время разбора текста
 */
sealed class Symbol : Expression() 

/**
 * Нетерминалы по сути описывают названия правил
 * В данных грамматиках в левой стороне правила может быть только нетерминал, 
 * т.к. грамматики эти контекстно- свободны
 */
data class NonTerminal(val name: String) : Symbol() {
  override fun toString(): String = name
}

/**
 * Терминалы- это символы из которых состоит алфавит языка, порождаемого грамматикой.
 * Терминалы не заменяются правилами
 */
data class Terminal(val value: String) : Symbol() {
  init {
    if (0 == value.length) throw WrongTerminalException()
  }
  override fun toString(): String = "\'" + value.replace("\'", "\\\'") + "\'"
}

typealias CharFilter = (Char?) -> Boolean

/**
 * Специальные символы - это такие терминалы, по которым нельзя сгенерировать текст, 
 * но которые позволяют текст разбирать
 * 
 * ну ладно, на самом деле они нетерминалы, потому что у них есть имя и они, вообще 
 * говоря, задают МНОЖЕСТВО (читай, Choise) из доступных символов. 
 * С другой стороны они скорее терминалы, ведь по ним сразу можно понять подходят ли 
 * символы из потока под данный символ
 * Именно поэтому они наследуют Symbol, а больше ничего не наследуют. Так надо
 */
data class SpecialSymbol(val name: String, val filter: CharFilter) : Symbol() {
  override fun toString(): String = name
  operator fun not(): SpecialSymbol = SpecialSymbol(name, { !filter(it) })
}

/**
 * Конец файла. Его роль очевидна
 */
class EndOfFile: Symbol(){
  override fun equals(other: Any?):Boolean = if (other is EndOfFile) true else false
  override fun hashCode(): Int = 1
}

/**
 * Последовательность - правило вида:
 * X = A B C ...
 */
data class Sequence(val parts: List<Expression>) : Expression() {
  constructor(vararg exprs: Expression) : this(exprs.toList())
  override fun toString(): String = parts.fold("", { acc, e -> acc + " " + e })
}

/**
 * Выбор варианта - правило вида
 * X = (A | B | C)
 */
data class Choise(val variants: List<Expression>) : Expression() {
  constructor(vararg exprs: Expression) : this(exprs.toList())
  override fun toString(): String = variants.fold("( ", { acc, e -> acc + e + " | " }).substringBeforeLast("|") + ")"
}

/**
 * Повторение - правило вида
 * X = { A }
 */
data class Repeat(val repeatable: Expression) : Expression() {
  override fun toString(): String = "{ " + repeatable + " }"
}

/**
 * Опциональность - 
 * X = [ A ]
 */
data class Maybe(val possible: Expression) : Expression() {
  override fun toString(): String = "[ " + possible + " ]"
}

/**
 * Грамматика  - набор правил вида X = ... 
 * Поскольку существует выражение выбора (@see Choise), 
 * символы с левой стороны правил могут не повторяться.
 * Это позволяет хранить правила в мапе
 */
data class Grammar(val rules: Map<NonTerminal, Expression>) {
  constructor(vararg pairs: Pair<NonTerminal, Expression>) : this(hashMapOf(*pairs))
  override fun toString(): String = rules.entries.fold("", { acc, entry -> acc + entry.key + " = " + entry.value + "\n" })
}
