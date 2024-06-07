package formallang.grammar

import org.junit.Test
import org.junit.Assert.*

class GrammarTest{
  val a =  NonTerminal("a")
  val b =  NonTerminal("b")
  val c =  NonTerminal("c")
  val A =  Terminal("a")
  val B =  Terminal("b")
  val C =  Terminal("c")
  val grammar = Grammar(
    a to Sequence(A,b,c),
    b to Choise(Sequence(B,c), Sequence(c,B), Repeat(B)),
    c to Sequence(C, Maybe(a))
  )
  val simplifiedGrammar = SimplifiedGrammar.fromGrammar(grammar)

  @Test
  fun grammarTest(){
    println(simplifiedGrammar)
    println(simplifiedGrammar.getPossibleRules(b, 'b'))

  }
}