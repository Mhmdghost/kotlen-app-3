package automata

/**
 * Состояние машины состояний. Типы состояний см
 *
 * @see automata.State.StateType
 */
interface State {
  /**
   * Тип состояния. UNFINISHED - не является конечным, FAIL- работа закончена неудачей, 
   * SUCCESS - работа закончена успешно
   */
  public enum class StateType {
    UNFINISHED, FAIL, SUCCESS
  }
  /**
   * Получить тип состояния
   */
  public fun getType(): StateType
}

/**
 * Недетерминированный конечный автомат. 
 * Это значит что из одного состояния автомат может перейти сразу в несколько.
 */
interface StateMachiene<T : State> {
  /**
   * Запустить автомат и получить одно из полученных состояний с типом SUCCESS или null 
   * если таких состояний нет
   * 
   * @return Полученное состояние с типом SUCCESS или null если таких нет
   * @param startState Начальное состояние атвомата
   */
  public fun runMachiene(startState: T): T?
}

/**
 * Фабрика недетерминированных конечных автоматов позволяет получить автомат 
 * с заданной функией для описания переходов
 */
interface StateMachieneFactory<T : State> {
  /**
   * Получить автомат с заданной функией для описания переходов
   */
  public fun getMachiene(transition: (T) -> Iterable<T>): StateMachiene<T>
  
  /**
   * Получить автомат с заданной функией для описания переходов
   */
  public fun getMachieneSuspend(transition: suspend (T) -> Iterable<T>): StateMachiene<T>
}
