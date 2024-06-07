package formallang.metainf

import formallang.ast.*
import formallang.grammar.*

/**
 * Процессор занимается пост-обработкой дерева.
 */
interface AstProcessor {
  public fun process(ast: Ast): Ast
}

/**
 * Этот класс удобно использовать когда можно пометить некоторые узлы как пригодные или
 * непригодые для обработки.  
 */
open class TaggedSet(
  val isTagged: (Node) -> Boolean,
  val nodeProcessor: (Node) -> List<Node>,
  val direction: ProcessingDirection
) : AstProcessor {
  /**
   * При обработке ROOT_FIRST обход происходит от корня к листьям, 
   * при обработке ROOT_LAST сначала обрабатываются все дети ветки, а потом сама ветка
   */
  public enum class ProcessingDirection {
    ROOT_FIRST, ROOT_LAST
  }

  private final fun processNodeRL(node: Node): List<Node> {
    var newNode = node 
    if (node is Branch){
      val newChildren = ArrayList<Node>()
      for (child in node.children) newChildren.addAll(processNodeRL(child))
      newNode = Branch(node.nonTerminal, node.rule, newChildren)
    }
    return if (isTagged(newNode)) nodeProcessor(newNode) else listOf(newNode)
  }

  private final fun processNodeRF(node: Node): List<Node> {
    if (isTagged(node))
      return nodeProcessor(node)
    var newNode = node
    if (node is Branch){
      val newChildren = ArrayList<Node>() 
      for (child in node.children) newChildren.addAll(processNodeRF(child))
      newNode = Branch(node.nonTerminal, node.rule, newChildren)
    }
    return listOf(newNode)
  }

  public final override fun process(ast: Ast): Ast =
    when (direction) {
      ProcessingDirection.ROOT_FIRST -> Ast(processNodeRF(ast.root).get(0))
      ProcessingDirection.ROOT_LAST -> Ast(processNodeRL(ast.root).get(0))
    }
}

/**
 * Этот класс применяется когда пригодные для обработки узлы образованы при разборе
 * определённго набора нетерминалов
 */
open class TaggedNonTerminalSet(
  val nonTerminals: Set<NonTerminal>,
  nodeProcessor: (Node) -> List<Node>,
  direction: ProcessingDirection
) : TaggedSet(
  { (it is Branch) && nonTerminals.contains(it.nonTerminal) },
  nodeProcessor,
  direction
)

/**
 * Этот класс применяется когда пригодные для обработки узлы образованы при разборе
 * определённго набора правил
 */
open class TaggedRuleSet(
  val rules: Set<SimplifiedRule>,
  nodeProcessor: (Node) -> List<Node>,
  direction: ProcessingDirection
) : TaggedSet(
  { (it is Branch) && rules.contains(it.rule) },
  nodeProcessor,
  direction
)
