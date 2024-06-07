package formallang.ast

import formallang.grammar.*
import kotlin.text.*

/**
 * Узел АСД. это как sealed interface, но таких не существует, поэтому вот
 */
sealed class Node

/**
 * Ветка AST. Знает по какому нетерминалу и правилу она получена
 */
class Branch(
  val nonTerminal: NonTerminal,
  val rule: SimplifiedRule,
  val children: List<Node>
) : Node(){
  public fun getString():String = children.fold("", {
    acc, nextNode -> acc + when (nextNode){
        is Leaf -> nextNode.getStringValue()
        is Branch -> nextNode.getString()
      }
  })
}

/**
 * Обобщённый лист АСД.
 */
sealed class Leaf : Node(){
  public abstract fun getStringValue(): String
}

/**
 * Лист полученный разбором терминала
 */
class TerminalLeaf(
  val terminal: Terminal
) : Leaf(){
  override public fun getStringValue() = terminal.value 
}

/**
 * Лист полученный заданным правилом для символа
 */
class SpecialLeaf(
  val specialSymbol: SpecialSymbol,
  val value: Char
) : Leaf(){
  override public fun getStringValue() = value.toString()
}

/**
 * Лист полученный в процессе разбора символа конца файла
 */
object EndOfFileLeaf : Leaf(){
  override public fun getStringValue() = ""
}

/**
 * Пустой лист. Кажется, не используется но если кто-то будет делать парсер 
 * получше, мб ему пригодится
 */
object EmptyLeaf : Leaf(){
  override public fun getStringValue() = ""
}

/**
 * Лист составленный из детей ветки. Знает свой нетерминал и значение
 */
class CompoundLeaf(
  val nonTerminal: NonTerminal, 
  val value: String
) : Leaf(){
  override public fun getStringValue() = value
}

/**
 * Абстрактное Синтаксическое Дерево. Знает свой корень
 */
class Ast(val root: Node){

  private fun decoration(offset: Int): String =
    if (offset < 0) throw IllegalArgumentException("offset must be positive") else 
      "|".repeat(offset)

  private fun toString(node: Node, offset: Int): String =
    when (node){
      is Leaf -> decoration(offset) + "'${node.getStringValue()}'\n"
      is Branch -> decoration(offset) + "${node.nonTerminal.name}\n" +
        node.children.map({toString(it, offset+1)})
          .fold("", String::plus)
    }

  override fun toString(): String = toString(root, 0) 
}