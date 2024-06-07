package forkable

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

/**
 * Поток, который может *разделиться*
 */
interface IForkableStream<T> : Forkable<IForkableStream<T>> {
  /**
   * Получить следующее значение из потока или null если поток кончился
   */
  public suspend fun next(): T?
}

/**
 * Узел очереди - буффера для элементов, которые ещё не прочитаны
 */
private open class StreamNode<T>(
  val value: T,
  nextProducer: () -> T
) {
  private val nextProducer: () -> T = nextProducer
  private var next: StreamNode<T>? = null
  private val nextMutex: Mutex = Mutex()

  /**
   * Получить следующий узел очереди
   */
  public suspend fun getNextNode(): StreamNode<T> {
    if (null == next) {
      nextMutex.withLock() {
        if (null == next)
          next = StreamNode<T>(nextProducer.invoke(), nextProducer)
      }
    }
    return next!!
  }
}

/**
 * Узел очереди, хранящий массив элементов. Кажется это должно слегка экономить память
 */
private class ArrayStreamNode<T>(
  value: ArrayList<T?>,
  val producer: () -> T?
) : StreamNode<ArrayList<T?>>(
  value, {
      val list = ArrayList<T?>(ArrayStreamNode.ARRAY_SIZE)
      repeat(ArrayStreamNode.ARRAY_SIZE) {
        list.add(producer())
      }
      list
    }
) {
  companion object {
    val ARRAY_SIZE: Int = 10
  }
}

/**
 * Поток, который может *разделиться*
 */
class ForkableStream<T> private constructor(
  node: StreamNode<ArrayList<T?>>,
  var elementIndex: Int
) : IForkableStream<T> {

  private var node: StreamNode<ArrayList<T?>> = node

  constructor(producer: () -> T?) : this(
    ArrayStreamNode<T>(arrayListOf(), producer),
    -1 // Потому что перед получением следующего элемента индекс увеличивается на 1
  ) {
    runBlocking {
      node = node.getNextNode()
    }
  }

  private var blocked: Boolean = false
  override fun isBlocked(): Boolean = blocked

  /**
   * Получить следующее значение из потока
   */
  override suspend fun next(): T? {
    if (blocked) throw AlreadyForkedException()
    elementIndex = elementIndex + 1
    if (elementIndex >= node.value.size) {
      elementIndex = 0
      node = node.getNextNode()
    }
    return node.value[elementIndex]
  }

  /**
   * *разделиться* @see forkable.Forkable#fork(Int)
   */
  override fun fork(count: Int): Iterable<ForkableStream<T>> {
    if (blocked) throw AlreadyForkedException()
    val list = ArrayList<ForkableStream<T>>(count)
    repeat(count) {
      list.add(ForkableStream<T>(node, elementIndex))
    }
    blocked = true
    return list
  }
}
