package automata

import kotlinx.coroutines.*

/**
 * Простейшая реализация недетерминированного конечного автомата.
 * Все переходы запускаются в одном потоке.
 */
class RegularMachiene<T : State>(
  val transition: (T) -> Iterable<T>
) : StateMachiene<T> {

  /**
   * Все текущие состояния машины хранятся в этом списке.
   */
  private var currentStates: List<T> = ArrayList<T>()

  /**
   * Выполнить все переходы для всех текущих состояний. Состояния с типом Fail удаляются.
   *
   * @return SUCCESS если найдено хотя бы одно состояние с таким типом.
   *          Оно же становится едтинственным в списке currentStates.
   *          FAIL если все состояния были удалены
   *          UNFINISHED если все состояния на данный момент имеют такой тип
   */
  private fun stepTransitions(): State.StateType {
    currentStates = currentStates.flatMap(transition).filter({
      it.getType() != State.StateType.FAIL })
    if (currentStates.size == 0)
      return State.StateType.FAIL
    var possibleState = currentStates.find { it.getType() == State.StateType.SUCCESS }
    if (possibleState != null) {
      currentStates = listOf(possibleState)
      return State.StateType.SUCCESS
    }
    currentStates.filter { it.getType() == State.StateType.UNFINISHED }
    return State.StateType.UNFINISHED
  }

  /**
   * Запустить автомат и выбрать первое полученное состояние с типом SUCCESS.
   * Если все полученные состояния имеют тип FAIL, результатом будет null
   *
   * @return первое состояние с типом SUCCESS или null если такого нет
   */
  override fun runMachiene(startState: T): T? {
    currentStates = arrayListOf(startState)
    var currentStateType = startState.getType()
    while (State.StateType.UNFINISHED == currentStateType) {
      currentStateType = stepTransitions()
    }
    return when (currentStateType) {
      State.StateType.SUCCESS -> currentStates.get(0)
      else -> null
    }
  }
}

/**
 * Выдаёт экземпляры [RegularMachiene<T>]
 */
class RegularMachieneFactory<T : State> : StateMachieneFactory<T> {
  /**
   * Получить экземпляр [automata.RegularMachiene] с указанной функией для описания переходов
   * из состояния в состояние
   */
  public override fun getMachiene(transition: (T) -> Iterable<T>): StateMachiene<T> =
    RegularMachiene(transition)
    
  /**
   * Получить экземпляр [RegularMachiene] с указанной функией для описания переходов
   * из состояния в состояние
   */
  public override fun getMachieneSuspend(transition: suspend (T) -> Iterable<T>): StateMachiene<T> =
    RegularMachiene({runBlocking{transition(it)}})
}
