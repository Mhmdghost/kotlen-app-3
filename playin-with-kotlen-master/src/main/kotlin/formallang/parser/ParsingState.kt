package formallang.parser

import automata.*
import forkable.*
import formallang.ast.*
import formallang.grammar.*
import kotlinx.coroutines.*

/**
 * Полезная нагрузка для листьев дерева, которое потом будем превращать в АСД
 */
private class LeafData(
  val symbol: Symbol,
  val value: String
)

/**
 * Полезная нагрузка для веток дерева, которое потом будем превращать в АСД
 */
private class BranchData(
  val nonTerminal: NonTerminal,
  val rule: SimplifiedRule
)

/**
 * Таколе дерево будем преобразовывать в АСД
 */
private typealias ParsingTree = ForkableTree<BranchData, LeafData>

/**
 * Такой поток используется как источник входных данных 
 */
private typealias CharacterStream = ForkableStream<Char>

/**
 * Преобразовать дерево разбора в АСД
 */
private fun ParsingTree.toAst(): Ast {
  return Ast(toAstNode((this.getRoot() as IBranch).getChildren().first()))
}

private fun toAstNode(node: ITreeNode<BranchData, LeafData>): Node =
  when (node) {
    is IBranch ->
      if (node.getChildren().size == 0)
        EmptyLeaf
      else
        Branch(
          node.getData().nonTerminal,
          node.getData().rule,
          node.getChildren().map(::toAstNode)
        )
    is ILeaf -> {
      val nodeData = node.getData()
      when (nodeData.symbol) {
        is Terminal -> TerminalLeaf(nodeData.symbol)
        is SpecialSymbol -> SpecialLeaf(nodeData.symbol, nodeData.value.get(0))
        is EndOfFile -> EndOfFileLeaf
        is NonTerminal -> if (0 < nodeData.value.length)
            CompoundLeaf(nodeData.symbol, nodeData.value)
          else
            EmptyLeaf
      }
    }
    else -> throw InvalidTreeException()
  }

/**
 * Состояние разбора. от него наследуются 3 состояния: неудачное, успешное и обычное
 */
sealed class ParsingState() : State {
  public abstract suspend fun transition(): Iterable<ParsingState>
}

/**
 * Обычное состояние разбора хранит информацию о дереве разбора, входном потоке и 
 * символе, который получен из этого потока, но ещё не отправлен в узел дерева разбора
 */
class RegularState private constructor(
  val grammar: SimplifiedGrammar,
  private val parsingTree: ParsingTree,
  val inputStream: CharacterStream,
  val knownChar: Char?
) : ParsingState() {
  /**
   * То, что автомат находится в этом состоянии значит, что разбор ещё не завершён
   */
  public override fun getType(): State.StateType = State.StateType.UNFINISHED

  /**
   *  Получить АСД, созданное на основе дерева разбора  
   */
  fun getAst(): Ast = parsingTree.toAst()

  private fun endOfFileTransition(marker: IBranch<BranchData, LeafData>):
      Iterable<ParsingState> {
    if (null == knownChar) {
      marker.addLeafChild(LeafData(EndOfFile(), ""))
      return arrayListOf(this)
    } else return arrayListOf(FailedState())
  }

  private suspend fun specialSymbolTransition(
    symbol: SpecialSymbol,
    marker: IBranch<BranchData, LeafData>
  ): Iterable<ParsingState> {
    if (symbol.filter(knownChar)) {
      marker.addLeafChild(LeafData(symbol, knownChar.toString()))
      return listOf(RegularState(grammar, parsingTree, inputStream,
          inputStream.next()))
    } else return arrayListOf(FailedState())
  }

  private suspend fun terminalTransition(
    symbol: Terminal,
    marker: IBranch<BranchData, LeafData>
  ): Iterable<ParsingState> {
    var char = knownChar
    for (i in 0..(symbol.value.length - 1)) {
      if (symbol.value.get(i) == char)
        char = inputStream.next()
      else
        return listOf(FailedState())
    }
    marker.addLeafChild(LeafData(symbol, ""))
    return listOf(RegularState(grammar, parsingTree, inputStream, char))
  }

  private fun nonTerminalTransition(symbol: NonTerminal):
      Iterable<ParsingState> {
    val branchList = grammar.getPossibleRules(symbol, knownChar)
      .map({ BranchData(symbol, it) })
    if (branchList.isEmpty()) return listOf(FailedState())
    val count = branchList.size
    val trees = (
      if (1 == count)
        listOf(parsingTree) 
      else 
        parsingTree.fork(count)
    ).iterator()
    val streams = inputStream.fork(count).iterator()
    val branches = branchList.iterator()
    val states = ArrayList<ParsingState>()
    repeat(count) {
      val newState =
        RegularState(grammar, trees.next(), streams.next(), knownChar)
      val newBranch = branches.next()
      val marker = newState.parsingTree.getMarker() as IBranch
      marker.addBranchChild(newBranch)
      newState.parsingTree.setMarker(marker.getChildren().last())
      states.add(newState)
    }
    return states
  }

  override suspend fun transition(): Iterable<ParsingState> {
    val marker = parsingTree.getMarker()
    if (!(marker is IBranch)) {
      throw IllegalStateException("We do not mark leaves")
    }
    val nextSymbolIndex = marker.getChildren().size
    if (marker.getData().rule.symbols.size == nextSymbolIndex) {
      val parent = marker.getParent()
      if (null == parent) return listOf(SuccessState(this))
      parsingTree.setMarker(parent)
      return listOf(this)
    }
    if (marker.getData().rule.symbols.size > nextSymbolIndex) {
      val nextSymbol = marker.getData().rule.symbols.get(nextSymbolIndex)
      when (nextSymbol) {
        is EndOfFile -> return endOfFileTransition(marker)
        is SpecialSymbol -> return specialSymbolTransition(nextSymbol, marker)
        is Terminal -> return terminalTransition(nextSymbol, marker)
        is NonTerminal -> return nonTerminalTransition(nextSymbol)
      }
    } else throw IllegalStateException("Children count cannot be more than symbols's count")
  }

  public constructor (
    grammar: SimplifiedGrammar,
    inputStream: CharacterStream,
    mainSymbol: Symbol
  ) : this(
    grammar,
    ParsingTree.branchTree(
      BranchData(NonTerminal(""),
      SimplifiedRule(mainSymbol))
    ),
    inputStream,
    runBlocking { inputStream.next() }
  )
}

/**
 * Состояние когда разбор завершён удачно и можно получить созданное АСД
 */
class SuccessState(
  val regularState: RegularState
) : ParsingState() {
  public override fun getType(): State.StateType = State.StateType.SUCCESS
  override suspend fun transition(): Iterable<ParsingState> = listOf(this)
  public fun getAst(): Ast = regularState.getAst()
}

/**
 * Состояние когда разбор завершён неудачно
 */
class FailedState : ParsingState() {
  public override fun getType(): State.StateType = State.StateType.FAIL
  override suspend fun transition(): Iterable<ParsingState> = listOf(this)
}
