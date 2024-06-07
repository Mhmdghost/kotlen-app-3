package forkable

/**
 * Стек который может *разделиться*
 */
interface IForkableStack<T> : Forkable<IForkableStack<T>> {
  /**
   * добавить элемент в стек
   */
  public fun push(element: T)
  /**
   * Посмотреть верхний элемент стека
   */
  public fun peek(): T? // null means that stack is empty
  /**
   * Получить и удалить верхний элемент стека
   */
  public fun pull(): T? // null means that stack is empty
}

/**
 * Узел стека, причём, самый обычный. По факту *разделяющийся* стек не отличается от
 * обычного ссылочного вообще ничем, кроме возможности *разделяться*
 */
private open class StackNode<T>(
  val data: T,
  val parent: StackNode<T>?
)

/**
 * Стек, который может *разделиться*
 */
class ForkableStack<T> private constructor(
  currentNode: StackNode<T>?
) : IForkableStack<T> {

  private var currentNode: StackNode<T>? = currentNode

  /**
   * Добавить элемент в стек
   */
  override fun push(element: T) {
    if (blocked) throw AlreadyForkedException()
    currentNode = StackNode(element, currentNode)
  }

  /**
   * Посмотреть элемент на верхушке стека
   */
  override fun peek(): T? = if (blocked) throw AlreadyForkedException()
    else currentNode?.data

  /**
   * Удалить элемент из стека
   */
  override fun pull(): T? {
    if (blocked) throw AlreadyForkedException()
    val result = currentNode?.data
    currentNode = currentNode?.parent
    return result
  }

  private var blocked = false
  override fun isBlocked(): Boolean = blocked
  /**
   * *разделиться* @see forkable.Forkable#fork(Int)
   */
  override fun fork(count: Int): Iterable<IForkableStack<T>> {
    val list = ArrayList<IForkableStack<T>>(count)
    repeat(count) {
      list.add(ForkableStack(currentNode))
    }
    return list
  }
}
