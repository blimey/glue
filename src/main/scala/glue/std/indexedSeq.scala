package glue
package std

import glue.typeclass.{Applicative, Foldable, Functor, Monoid}

object indexedSeq extends IndexedSeqFunctions with IndexedSeqSyntax with IndexedSeqImplicits

trait IndexedSeqFunctions {
  def foldMapB[A, B](as: IndexedSeq[A])(f: A => B)(implicit M: Monoid[B]): B =
    if (as.length == 0) M.unit
    else if (as.length == 1) f(as(0))
    else {
      val (left, right) = as.splitAt(as.length / 2)
      M.combine(foldMapB(left)(f), foldMapB(right)(f))
    }
}

trait IndexedSeqSyntax {
  implicit class IndexedSeqOps[A](self: IndexedSeq[A]) {
    def foldMapB[B](f: A => B)(mb: Monoid[B]): B = indexedSeq.foldMapB(self)(f)(mb)
  }
}

trait IndexedSeqImplicits {
  implicit val indexedSeqIsFoldable: Foldable[IndexedSeq] = new Foldable[IndexedSeq] {
    def foldLeft[A, B](as: IndexedSeq[A], z: B)(f: (B, A) => B): B =
      as.foldLeft(z)(f)
    def foldRight[A, B](as: IndexedSeq[A], z: B)(f: (A, B) => B): B =
      as.foldRight(z)(f)
    def foldMap[A, B](as: IndexedSeq[A])(f: A => B)(implicit M: Monoid[B]): B =
      indexedSeq.foldMapB(as)(f)(M)
  }

  private implicit val indexedSeqIsFunctor: Functor[IndexedSeq] = new Functor[IndexedSeq] {
    def map[A, B](fa: IndexedSeq[A])(f: A => B): IndexedSeq[B] = fa map f
  }

  implicit val indexedSeqIsApplicative: Applicative[IndexedSeq] = new Applicative[IndexedSeq] {
    val functor: Functor[IndexedSeq] = Functor[IndexedSeq]
    def pure[A](a: => A): IndexedSeq[A] = IndexedSeq(a)
    def apply[A, B](f: IndexedSeq[A => B])(as: IndexedSeq[A]): IndexedSeq[B] = as flatMap { a => f.map(_(a)) }
  }
}