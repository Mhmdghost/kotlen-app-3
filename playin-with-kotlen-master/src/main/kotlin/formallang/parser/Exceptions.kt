package formallang.parser

/** 
 * По идее это исключение не выбрасывается никогда, так как пользователь не может сам
 * создать ITreeNode и добавить его в дерево, тем не менее, выбрасывается если в дереве есть узел, 
 * который не является ни листом, ни веткой
 */
class InvalidTreeException: 
  Exception("used ForkableTree cannot be transformed to AST")