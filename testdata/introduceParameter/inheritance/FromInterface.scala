1
trait A {
  def foo(x: Int)
}

class B extends A {
  def foo(x: Int) = /*start*/1/*end*/
}
/*
1
trait A {
  def foo(x: Int, param: Unit)
}

class B extends A {
  def foo(x: Int, param: Unit) = /*start*/param/*end*/
}
*/