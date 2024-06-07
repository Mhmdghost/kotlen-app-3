package forkable

/**
 * Выбрасывается при попытке взаимодействия с Forkable объектом у которого уже был 
 * вызван метод Forkable#fork(Int)
 */
class AlreadyForkedException: 
  Exception("Cannot call forkable object's methods after it was forked")