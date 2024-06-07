package forkable

import java.util.Collections
import java.util.WeakHashMap

/**
 * Узел дерева, знает своего родителя
 */
interface ITreeNode<BranchData, LeafData> {
  /**
   * Получить родительский узел
   */
  public fun getParent(): ITreeNode<BranchData, LeafData>?
}

/**
 * Ветка дерева, знает своих детей, может их добавлять в конец списка детей 
 * (удалять не может). Хранит какие-то полезные данные.
 */
interface IBranch<BranchData, LeafData> : ITreeNode<BranchData, LeafData> {
  /**
   * Получить список детей
   */
  public fun getChildren(): List<ITreeNode<BranchData, LeafData>>
  /**
   * Добавить ветку с указанными данными в конец списка
   */
  public fun addBranchChild(branchData: BranchData)
  /**
   * Добавить лист с ука
   */
  public fun addLeafChild(leafData: LeafData)
  /**
   * Получить хранимые данные
   */
  public fun getData(): BranchData
  /**
   * Задать новые хранимые данные
   */
  public fun setData(data: BranchData)
}

/**
 * Лист дерева. Хранит какие-то полезные данные
 */
interface ILeaf<BranchData, LeafData> : ITreeNode<BranchData, LeafData> {
  /**
   * Получить хранимые данные
   */
  public fun getData(): LeafData
  /**
   * Задать новые хранимые данные
   */
  public fun setData(data: LeafData)
}

/**
 * Дерево, которое знает свой корень и ещё хранит заданный отмеченный узел
 */
interface ITree<BranchData, LeafData> {
  /**
   * Получить корневой узел дерева
   */
  public fun getRoot(): ITreeNode<BranchData, LeafData>
  /**
   * Получить отмеченный узел. По умолчанию тот же что и forkable.ITree#getRoot()
   */
  public fun getMarker(): ITreeNode<BranchData, LeafData>
  /**
   * Отметить заданный узел. Отмечать узлы чужого дерева можно, но не нужно.
   */
  public fun setMarker(node: ITreeNode<BranchData, LeafData>)
}

/**
 * Просто узел дерева
 */
private open class TreeNode<BranchData, LeafData>(
  parent: ITreeNode<BranchData, LeafData>?
) : ITreeNode<BranchData, LeafData> {
  private val parent: ITreeNode<BranchData, LeafData>? = parent
  override fun getParent(): ITreeNode<BranchData, LeafData>? = parent
}

/**
 * Лист дерева. Хранит полезные данные.
 */
private class Leaf<BranchData, LeafData>(
  parent: ITreeNode<BranchData, LeafData>?,
  data: LeafData
) : TreeNode<BranchData, LeafData>(parent), ILeaf<BranchData, LeafData> {
  private var data: LeafData = data
  override fun getData(): LeafData = data
  override fun setData(data: LeafData) { this.data = data }
}

/**
 * Ветка дерева, которая хранится в графе на самом деле. Содержит полезные данные.
 */
private class RealBranch<BranchData, LeafData>(
  parent: ITreeNode<BranchData, LeafData>?,
  data: BranchData
) : TreeNode<BranchData, LeafData>(parent) {
  companion object {
    private val INITIAL_MAP_CAPACITY = 1
  }

  /**
   * Хранение детей в слабой хэшмапе обеспечивает возможность удаления экземпляров
   * ForkableTree, которые используют данный узел
   */
  private val children = Collections.synchronizedMap(
    WeakHashMap<ForkableTree<BranchData, LeafData>, MutableList<TreeNode<BranchData, LeafData>>>(
      RealBranch.INITIAL_MAP_CAPACITY
    )
  ) 
  // Наверное было бы лучше использовать тут мапу оптимизированную для малого числа вхождений

  private var data: BranchData = data
  fun getData(): BranchData = data
  fun setData(data: BranchData) { this.data = data }

  /**
   * Получить список детей, доступный указанному экземпляру ForkableTree
   */
  fun getChildren(caller: ForkableTree<BranchData, LeafData>): List<ITreeNode<BranchData, LeafData>> =
    ((caller.inherited union hashSetOf(caller)) intersect (children.keys)).map { children.get(it)!! }
      .fold(
        emptyList<ITreeNode<BranchData, LeafData>>(),{acc, next -> acc+next}
      ).map { if (it is RealBranch) Branch(it, caller) else it }

  private fun getOrCreateChildrenList(caller: ForkableTree<BranchData, LeafData>):
      MutableList<ITreeNode<BranchData, LeafData>> = (
          children.get(caller) ?: {
          val list = arrayListOf<TreeNode<BranchData, LeafData>>()
          children.put(caller, list)
          list
        }()) as MutableList<ITreeNode<BranchData, LeafData>>
  
  /**
   * Добавить лист, доступный только указанному экземпляру ForkableTree и его наследникам
   */
  fun addLeafChild(caller: ForkableTree<BranchData, LeafData>, data: LeafData) {
    getOrCreateChildrenList(caller).add(Leaf(this, data))
  }

  /**
   * Добавить ветку, доступную только указанному экземпляру ForkableTree и его наследникам
   */
  fun addBranchChild(caller: ForkableTree<BranchData, LeafData>, data: BranchData) {
    getOrCreateChildrenList(caller).add(
      RealBranch(this, data)
    )
  }

  /**
   * Получить родительский узел данного узла
   */
  fun getParent(caller: ForkableTree<BranchData, LeafData>):
      ITreeNode<BranchData, LeafData>? {
    val parent = getParent()
    if (parent is RealBranch)
      return Branch(parent, caller)
    else
      return parent
  }
}

/**
 * Ветка дерева. По сути обёртка над RealBranch, которую никто никогда не создаст сам.
 * Блокируется вместе с блокировкой соответствующего экземпляра ForkableTree
 */
private class Branch<BranchData, LeafData> (
  val realBranch: RealBranch<BranchData, LeafData>,
  val caller: ForkableTree<BranchData, LeafData>
) : IBranch<BranchData, LeafData> {
  override fun getChildren(): List<ITreeNode<BranchData, LeafData>> =
    if (caller.isBlocked()) throw AlreadyForkedException() else 
      realBranch.getChildren(caller)
  override fun addBranchChild(branchData: BranchData) =
    if (caller.isBlocked()) throw AlreadyForkedException() else 
      realBranch.addBranchChild(caller, branchData)
  override fun addLeafChild(leafData: LeafData) =
    if (caller.isBlocked()) throw AlreadyForkedException() else 
      realBranch.addLeafChild(caller, leafData)
  override fun getData(): BranchData =
    if (caller.isBlocked()) throw AlreadyForkedException() else 
      realBranch.getData()
  override fun setData(data: BranchData) =
    if (caller.isBlocked()) throw AlreadyForkedException() else 
      realBranch.setData(data)
  override fun getParent(): ITreeNode<BranchData, LeafData>? =
    if (caller.isBlocked()) throw AlreadyForkedException() else 
      realBranch.getParent(caller)
}

/**
 * Дерево, которое может *разделиться*
 */
class ForkableTree<BranchData, LeafData> private constructor(
  val inherited: Set<ForkableTree<BranchData, LeafData>>
) : Forkable<ForkableTree<BranchData, LeafData>>, ITree<BranchData, LeafData> {

  private lateinit var root: ITreeNode<BranchData, LeafData>
  private lateinit var marker: ITreeNode<BranchData, LeafData>

  private constructor(
    inherited: Set<ForkableTree<BranchData, LeafData>>,
    root: ITreeNode<BranchData, LeafData>
  ) : this(inherited) {
    this.root = root
  }

  companion object {
    /**
     * Поскольку корень не может быть null, а возможность самостоятельного добавления 
     * узлов в пустое дерево отсутствует, используем фабрики деревьев
     */
    private fun <NodeData, BranchData, LeafData> initTree(
      data: NodeData, 
      nodeConstructor: (ITreeNode<BranchData, LeafData>?, NodeData) -> 
          ITreeNode<BranchData, LeafData>
    ) : ForkableTree<BranchData, LeafData> {
      val tree = ForkableTree(HashSet<ForkableTree<BranchData, LeafData>>())
      tree.root = nodeConstructor(null, data)
      tree.marker = tree.getRoot()
      return tree
    }
    /** 
     * Получить дерево, состоящее только из листа с указанными данными
     */
    public fun <BranchData, LeafData> leafTree(rootData: LeafData):
        ForkableTree<BranchData, LeafData> = 
      initTree<LeafData, BranchData, LeafData>(rootData, {parent ,data -> Leaf(parent,data)})
    /**
     * Получить дерево, состоящее только из ветки с указанными данными
     */
    public fun <BranchData, LeafData> branchTree(rootData: BranchData):
        ForkableTree<BranchData, LeafData> = 
      initTree<BranchData, BranchData, LeafData>(rootData, {parent ,data -> RealBranch(parent,data)})
  }

  private var blocked: Boolean = false
  override fun isBlocked(): Boolean = blocked

  /**
   * Получить отмеченный узел
   */
  override fun getMarker() =
    if (blocked) throw AlreadyForkedException() else marker
  /**
   * Отметить заданный узел
   */
  override fun setMarker(node: ITreeNode<BranchData, LeafData>) {
    if (blocked) throw AlreadyForkedException()
    this.marker = node
  }

  /**
   * Получить корневой узел дерева
   */
  override fun getRoot(): ITreeNode<BranchData, LeafData> =
    if (blocked) throw AlreadyForkedException() else 
      if (root is RealBranch) Branch(root as RealBranch, this) else 
        root

  /**
   * *разделиться*
   */
  override fun fork(count: Int): Iterable<ForkableTree<BranchData, LeafData>> {
    if (blocked) throw AlreadyForkedException()
    val list = ArrayList<ForkableTree<BranchData, LeafData>>(count)
    repeat(count) {
      val tree = ForkableTree(this.inherited union hashSetOf(this), root)
      if (marker is Branch) {
        tree.setMarker(Branch((marker as Branch).realBranch, tree))
      } else {
        tree.setMarker(marker)
      }
      list.add(tree)
    }
    blocked = true
    return list
  }
}
