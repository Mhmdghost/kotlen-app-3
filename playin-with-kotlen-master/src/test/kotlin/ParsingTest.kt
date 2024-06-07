import automata.*
import formallang.ast.*
import formallang.grammar.*
import formallang.metainf.*
import formallang.parser.*
import org.junit.Assert.*
import org.junit.Test

class ParsingTest {

  val digit = SpecialSymbol("digit") { if (null == it) false else Character.isDigit(it) }
  val whitespace = SpecialSymbol("whitespace") { if (null == it) false else Character.isWhitespace(it) }
  val plus = Terminal("+")
  val leftP = Terminal("(")
  val rightP = Terminal(")")
  val number = NonTerminal("Number")
  val numberRule = Sequence(digit, Repeat(digit))
  val spaces = NonTerminal("Spaces")
  val spacesRule = Repeat(whitespace)
  val expression = NonTerminal("expression")
  val expressionRule = Choise(
    number,
    Sequence(leftP, spaces, expression, spaces,
      Repeat(Sequence(plus, spaces, expression)), spaces, rightP)
  ) // ( Number | '(' expression {'+' expression} ')'
  val mainSymbol = NonTerminal("Main")
  val mainRule = Sequence(expression, EndOfFile())

  val grammar = Grammar(
    number to numberRule,
    spaces to spacesRule,
    expression to expressionRule,
    mainSymbol to mainRule
  )

  fun regular() {
    val regularParser = Parser(RegularMachieneFactory<ParsingState>(), grammar, mainSymbol,
      RemoveEmpties,
      StringifyNonTerminals(number)
    )
    val normalTree = regularParser.parse("((12+13) + 158   )".reader())
    assertNotNull(normalTree)
    println(normalTree)
    val brokenTree = regularParser.parse("((12+13) + 158   ".reader())
    assertNull(brokenTree)
    println(brokenTree)
  }

  fun async() {
    println("ASYNC!!!")
    val asyncParser = Parser(AsyncMachieneFactory<ParsingState>(), grammar, mainSymbol,
      RemoveEmpties,
      StringifyNonTerminals(number)
    )
    val normalTree = asyncParser.parse("((12+13) + 158   )".reader())
    assertNotNull(normalTree)
    println(normalTree)
    val brokenTree = asyncParser.parse("((12+13) + 158   ".reader())
    assertNull(brokenTree)
    println(brokenTree)
  }

  @Test 
  fun timeTest(){
    val regularTime = kotlin.system.measureTimeMillis{ regular() }  
    val asyncTime = kotlin.system.measureTimeMillis{ async() }  
    //assertTrue( asyncTime < regularTime)
    println("Async time: $asyncTime, Regular time: $regularTime")
  }
}
