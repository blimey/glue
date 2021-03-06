package glue
package std

import glue.typeclass.{Applicative, Foldable, Functor, Monad, Monoid, Traverse}

object stream extends StreamImplicits

trait StreamImplicits {
  private implicit val streamIsFoldable: Foldable[Stream] = new Foldable[Stream] {
    def foldLeft[A, B](as: Stream[A], z: B)(f: (B, A) => B): B =
      as.foldLeft(z)(f)
    def foldRight[A, B](as: Stream[A], z: B)(f: (A, B) => B): B =
      as.foldRight(z)(f)
    def foldMap[A, B](as: Stream[A])(f: A => B)(implicit M: Monoid[B]): B =
      as.foldLeft(M.unit)((b, a) => M.combine(b, f(a)))
  }

  private implicit val streamIsFunctor: Functor[Stream] = new Functor[Stream] {
    def map[A, B](as: Stream[A])(f: A => B): Stream[B] = as map f
  }

  implicit val streamIsTraversable: Traverse[Stream] = new Traverse[Stream] {
    val foldable: Foldable[Stream] = Foldable[Stream]
    val functor: Functor[Stream] = Functor[Stream]
    def traverse[G[_], A, B](as: Stream[A])(f: A => G[B])(implicit G: Applicative[G]): G[Stream[B]] =
      as.foldRight(G.pure(Stream[B]())) { (a, gl) =>
        G.map2(f(a), gl) { (b, l) => b #:: l }
      }
  }

  private implicit val streamIsApplicative: Applicative[Stream] = new Applicative[Stream] {
    val functor: Functor[Stream] = Functor[Stream]
    def pure[A](a: => A): Stream[A] = Stream.continually(a)
    def apply[A, B](fs: Stream[A => B])(as: Stream[A]): Stream[B] =
      as zip fs map { case (a, f) => f(a) }
  }

  implicit val streamIsMonad: Monad[Stream] = new Monad[Stream] {
    val applicative: Applicative[Stream] = Applicative[Stream]
    def flatMap[A, B](as: Stream[A])(f: A => Stream[B]): Stream[B] = as flatMap f
  }

  implicit def streamIsMonoid[A]: Monoid[Stream[A]] = new Monoid[Stream[A]] {
    val unit: Stream[A] = Stream.empty
    def combine(l: Stream[A], r: Stream[A]): Stream[A] = l ++ r
  }
}