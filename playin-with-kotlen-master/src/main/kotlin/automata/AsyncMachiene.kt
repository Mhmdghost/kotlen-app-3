package automata

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.sync.*

/**
 * Этот класс должен уметь распределять обработку состояний по корутинам
 */
class AsyncMachiene<T : State> (
  val transition: suspend (T) -> Iterable<T>
) : StateMachiene<T> {

  /**
   * Сообщение в очереди сообщений
   */
  private interface Message<S : State>
  /**
   * Сообщение о том что начата новая корутина
   */
  private class JobStartsMessage<S : State>(val count: Int) : Message<S>
  /**
   * Сообщение о том что обрабатываемое состояние оказалось неудачным
   */
  private class JobFailsMessage<S : State>(val count: Int) : Message<S>
  /**
   * Сообщение о том что обрабатываемое состояние оказалось удачным
   */
  private class SuccessMessage<S : State> (val state: S) : Message<S>

  /**
   * Очередь сообщений
   */
  private val messages = Channel<Message<T>>()

  /**
   * Выполнить переходы состояния, попутно сообщить об этом в очередь сообщений
   */
  private suspend fun doTransition(state: T) {
    val results = transition(state).filter { State.StateType.FAIL != it.getType() }
    if (results.isEmpty()) {
      messages.send(JobFailsMessage(1))
      return
    }
    val good = results.find { State.StateType.SUCCESS == it.getType() }
    if (null != good) {
      messages.send(SuccessMessage(good))
      return
    }
    messages.send(JobStartsMessage(results.size - 1))
    results.forEach { s -> GlobalScope.launch { doTransition(s) } }
  }

  /**
   * Запустить автомат и выбрать первое полученное состояние с типом SUCCESS.
   * Если все полученные состояния имеют тип FAIL, результатом будет null
   *
   * @return первое состояние с типом SUCCESS или null если такого нет
   */
  override fun runMachiene(startState: T): T? {
    var result: T? = null
    runBlocking {
      val mainJob = launch {
        messages.send(JobStartsMessage(1))
        doTransition(startState)
      }
      var jobsCount: Int = 0
      var running = true
      launch {
        while (running) {
          val currentMessage = messages.receive()
          when (currentMessage) {
            is SuccessMessage -> {
              result = currentMessage.state
              running = false
            }
            is JobFailsMessage -> {
              jobsCount -= 1
              if (0 == jobsCount) running = false
            }
            is JobStartsMessage ->
              jobsCount += currentMessage.count
          }
        }
        mainJob.cancelChildren()
        mainJob.cancelAndJoin()
      }
    }
    return result
  }
}

class AsyncMachieneFactory<T : State> : StateMachieneFactory<T> {
  public override fun getMachiene(transition: (T) -> Iterable<T>): StateMachiene<T> =
    AsyncMachiene({ transition(it) })
  public override fun getMachieneSuspend(transition: suspend (T) -> Iterable<T>): StateMachiene<T> =
    AsyncMachiene(transition)
}
