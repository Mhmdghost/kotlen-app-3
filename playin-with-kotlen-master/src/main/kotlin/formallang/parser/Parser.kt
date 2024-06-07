package formallang.parser

import forkable.*
import formallang.ast.*
import formallang.grammar.*
import formallang.metainf.*
import automata.*
import java.io.Reader

/**
 * Класс для разбора текста по заданной грамматике.
 * Всё что он делает - запускает автомат с состояниями ParsingState
 */
class Parser private constructor(
  stateMachieneFactory: StateMachieneFactory<ParsingState>,
  val grammar: SimplifiedGrammar,
  val mainSymbol: Symbol,
  val processors: Iterable<AstProcessor>
) {
  val stateMachiene : StateMachiene<ParsingState>

  init {
    stateMachiene = stateMachieneFactory.getMachieneSuspend({ it.transition() })
  }

  constructor(
    stateMachieneFactory: StateMachieneFactory<ParsingState>,
    extendedGrammar: Grammar,
    mainSymbol: Symbol,
    vararg processorsList: AstProcessor
  ) : this(
    stateMachieneFactory,  
    SimplifiedGrammar.fromGrammar(extendedGrammar), 
    mainSymbol, 
    ArrayList<AstProcessor>()
  ) {
    val procList = (this.processors as MutableList)
    procList.add(UnfoldNonTerminals(this.grammar.rules.keys - extendedGrammar.rules.keys))
    procList.addAll(processorsList)
  }

  constructor(
    stateMachieneFactory: StateMachieneFactory<ParsingState>,
    simplifiedGrammar: SimplifiedGrammar,
    mainSymbol: Symbol,
    vararg processorsList: AstProcessor
  ) : this(
    stateMachieneFactory,
    simplifiedGrammar, 
    mainSymbol, 
    ArrayList<AstProcessor>()
  ) {
    val procList = (this.processors as MutableList)
    procList.addAll(processorsList)
  }

  private fun justParse(reader: Reader): Ast? {
    val state = stateMachiene.runMachiene(RegularState(grammar, ForkableStream({ 
      val result = reader.read() 
      if (-1 == result) null else result.toChar()
    }), mainSymbol))
    return when(state?.getType()){
      null -> null
      State.StateType.FAIL -> null
      State.StateType.UNFINISHED -> throw IllegalStateException("Cannot finish with unfinished state")
      State.StateType.SUCCESS -> (state as SuccessState).getAst()
    }
  }

  /**
   * Возвращает экземпляр Ast при удачном завершении разбора и null при неудачном
   */
  public fun parse(reader: Reader): Ast? {
    val initialTree = justParse(reader) ?: return null
    return processors.fold(initialTree) {
      acc, nextProc -> nextProc.process(acc)
    }
  }
}
