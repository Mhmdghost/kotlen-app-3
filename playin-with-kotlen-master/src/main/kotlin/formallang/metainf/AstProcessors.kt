package formallang.metainf

import formallang.ast.*
import formallang.grammar.*

/**
 * Преобразует ветки с указанными нетерминалами в formallang.grammar.CompoundLeaf
 */
class StringifyNonTerminals(nonTerminals: Set<NonTerminal>) :
  TaggedNonTerminalSet(
    nonTerminals,
    {
      when (it) {
        is Leaf -> listOf(it)
        is Branch -> listOf(CompoundLeaf(it.nonTerminal, it.getString()))
      }
    },
    ProcessingDirection.ROOT_FIRST
  ) {
  constructor(vararg nonTerminalList: NonTerminal) : this(hashSetOf<NonTerminal>()) {
    (nonTerminals as MutableSet<NonTerminal>).addAll(nonTerminalList)
  }
}

/**
 * Преобразует ветки с указанными нетерминалами в наборы из детей и присоединяет их 
 * к родителям этих веток 
 */
class UnfoldNonTerminals(nonTerminals: Set<NonTerminal>) :
  TaggedNonTerminalSet(
    nonTerminals,
    {
      when (it) {
        is Leaf -> listOf(it)
        is Branch -> it.children
      }
    },
    ProcessingDirection.ROOT_LAST
  ) {
  constructor(vararg nonTerminalList: NonTerminal) : this(hashSetOf<NonTerminal>()) {
    (nonTerminals as MutableSet<NonTerminal>).addAll(nonTerminalList)
  }
}

/**
 * Удаляет пустые листья и ветки
 */
object RemoveEmpties : TaggedSet(
  { 
    when (it) {
      is Leaf -> it.getStringValue() == ""
      is Branch -> it.children.size == 0
    }
  },
  {emptyList()},
  ProcessingDirection.ROOT_LAST
)