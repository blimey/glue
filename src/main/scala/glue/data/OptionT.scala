package glue
package data

import glue.std.option._

case class OptionT[F[_], A](run: F[Option[A]]) {
  def map[B](f: A => B)(implicit F: Functor[F]): OptionT[F, B] = OptionT(F.map(run)(_.map(f)))

  def mapT[G[_], B](f: F[Option[A]] => G[Option[B]]): OptionT[G, B] = OptionT(f(run))

  def mapK[G[_]](k: NaturalTransformation[F, G]): OptionT[G, A] = OptionT(k(run))

  def mapF[B](f: A => F[B])(implicit F: Monad[F]): OptionT[F, B] = OptionT {
    F.flatMap(run) {
      case None => F.pure(none[B])
      case Some(a) => F.map(f(a))(some(_))
    }
  }

  def flatMap[B](f: A => OptionT[F, B])(implicit F: Monad[F]): OptionT[F, B] = OptionT {
    F.flatMap(run) {
      case None => F.pure(none[B])
      case Some(a) => f(a).run
    }
  }

  def flatMapF[B](f: A => F[Option[B]])(implicit F: Monad[F]): OptionT[F, B] = OptionT {
    F.flatMap(run) {
      case None => F.pure(none[B])
      case Some(a) => f(a)
    }
  }

  def apply[B](of: => OptionT[F, A => B])(implicit F: Monad[F]): OptionT[F, B] = OptionT {
    F.flatMap(of.run) {
      case None => F.pure(none[B])
      case Some(f) => F.map(run)(_ map f)
    }
  }

  def map2[B, C](ob: OptionT[F, B])(f: (A, B) => C)(implicit F: Applicative[F]): OptionT[F, C] = OptionT {
    F.map2(run, ob.run) {
      case (None, _) | (_, None) => none[C]
      case (l, r) => l.flatMap(a => r.map(b => f(a, b)))
    }
  }

  def foldLeft[B](z: B)(f: (B, A) => B)(implicit F: Foldable[F]): B =
    F.compose(optionIsTraversable.foldable).foldLeft(run, z)(f)

  def foldRight[B](z: B)(f: (A, B) => B)(implicit F: Foldable[F]): B =
    F.compose(optionIsTraversable.foldable).foldRight(run, z)(f)

  def foldMap[B](f: A => B)(implicit F: Foldable[F], M: Monoid[B]): B =
    F.compose(optionIsTraversable.foldable).foldMap(run)(f)

  def traverse[G[_]: Applicative, B](f: A => G[B])(implicit T: Traverse[F]): G[OptionT[F, B]] =
    Applicative[G].map(T.compose(Traverse[Option]).traverse(run)(f))(OptionT(_))

  def isEmpty(implicit F: Functor[F]): F[Boolean] = F.map(run)(_.isEmpty)

  def isDefined(implicit F: Functor[F]): F[Boolean] = isEmpty

  def nonEmpty(implicit F: Functor[F]): F[Boolean] = F.map(run)(_.nonEmpty)

  def exists(p: A => Boolean)(implicit F: Functor[F]): F[Boolean] = F.map(run)(_.exists(p))

  def filter(p: A => Boolean)(implicit F: Functor[F]): OptionT[F, A] = OptionT(F.map(run)(_.filter(p)))

  def withFilter(p: A => Boolean)(implicit F: Functor[F]): OptionT[F, A] = filter(p)

  def filterNot(p: A => Boolean)(implicit F: Functor[F]): OptionT[F, A] = OptionT(F.map(run)(_.filterNot(p)))

  def fold[B](z: => B)(f: A => B)(implicit F: Functor[F]): F[B] = F.map(run)(_.fold(z)(f))

  def forall(p: A => Boolean)(implicit F: Functor[F]): F[Boolean] = F.map(run)(_.forall(p))

  def getOrElse[B >: A](z: => B)(implicit F: Functor[F]): F[B] = F.map(run)(_.getOrElse(z))

  def getOrElseF[B >: A](z: => F[B])(implicit F: Monad[F]): F[B] = F.flatMap(run)(_.fold(z)(F.unit(_)))

  def orElse[B >: A](z: => Option[B])(implicit F: Functor[F]): OptionT[F, B] = OptionT(F.map(run)(_ orElse z))

  def orElseF(z: => F[Option[A]])(implicit F: Monad[F]): OptionT[F, A] = OptionT {
    F.flatMap(run) {
      case None => z
      case s @ Some(_) => F.unit(s)
    }
  }

  def orElse(z: => OptionT[F, A])(implicit F: Monad[F]): OptionT[F, A] = this orElseF z.run

  def toLeftEither[R](right: => R)(implicit F: Functor[F]): EitherT[F, A, R] = EitherT {
    fold[Either[A, R]](Right(right))(Left(_))
  }

  def toRightEither[L](left: => L)(implicit F: Functor[F]): EitherT[F, L, A] = EitherT {
    fold[Either[L, A]](Left(left))(Right(_))
  }
}

object OptionT extends OptionTFunctions {
  object implicits extends OptionTImplicits
}

trait OptionTFunctions {
  def optionT[F[_]]: NaturalTransformation[({type f[x] = F[Option[x]]})#f, ({type f[x] = OptionT[F, x]})#f] =
    new NaturalTransformation[({type f[x] = F[Option[x]]})#f, ({type f[x] = OptionT[F, x]})#f] {
      def apply[A](fo: F[Option[A]]): OptionT[F, A] = OptionT(fo)
    }

  def someT[F[_]: Applicative, A](a: => A): OptionT[F, A] = OptionT(Applicative[F].pure(some(a)))
  def noneT[F[_]: Applicative, A]: OptionT[F, A] = OptionT(Applicative[F].pure(none[A]))

  def liftK[F[_]: Functor]: NaturalTransformation[F, ({type f[x] = OptionT[F, x]})#f] =
    new NaturalTransformation[F, ({type f[x] = OptionT[F, x]})#f] {
      def apply[A](fa: F[A]): OptionT[F, A] = liftF(fa)
    }
  def liftF[F[_]: Functor, A](fa: F[A]): OptionT[F, A] = OptionT(Functor[F].map(fa)(Some(_)))
}

trait OptionTImplicits {
  private implicit def optionTisFunctor[F[_]: Functor]: Functor[({type f[x] = OptionT[F, x]})#f] =
    new Functor[({type f[x] = OptionT[F, x]})#f] {
      def map[A, B](o: OptionT[F, A])(f: A => B): OptionT[F, B] = o map f
    }

  implicit def optionTisMonad[F[_]: Monad: Applicative: Functor]: Monad[({type f[x] = OptionT[F, x]})#f] =
    new Monad[({type f[x] = OptionT[F, x]})#f] {
      val applicative: Applicative[({type f[x] = OptionT[F, x]})#f] = new Applicative[({type f[x] = OptionT[F, x]})#f] {
        val functor: Functor[({type f[x] = OptionT[F, x]})#f] = Functor[({type f[x] = OptionT[F, x]})#f]
        def pure[A](a: => A): OptionT[F, A] = OptionT(Applicative[F].pure(some(a)))
        def apply[A, B](of: OptionT[F, A => B])(oa: OptionT[F, A]): OptionT[F, B] = oa.apply(of)
      }
      def flatMap[A, B](o: OptionT[F, A])(f: A => OptionT[F, B]): OptionT[F, B] = o flatMap f
    }

  private implicit def optionTisFoldable[F[_]: Foldable]: Foldable[({type f[x] = OptionT[F, x]})#f] =
    new Foldable[({type f[x] = OptionT[F, x]})#f] {
      def foldLeft[A, B](o: OptionT[F, A], z: B)(f: (B, A) => B): B = o.foldLeft(z)(f)
      def foldRight[A, B](o: OptionT[F, A], z: B)(f: (A, B) => B): B = o.foldRight(z)(f)
      def foldMap[A, B](o: OptionT[F, A])(f: A => B)(implicit M: Monoid[B]): B = o foldMap f
    }

  implicit def optionTisTraversable[F[_]: Traverse: Functor: Foldable]: Traverse[({type f[x] = OptionT[F, x]})#f] =
    new Traverse[({type f[x] = OptionT[F, x]})#f] {
      val foldable: Foldable[({type f[x] = OptionT[F, x]})#f] = Foldable[({type f[x] = OptionT[F, x]})#f]
      val functor: Functor[({type f[x] = OptionT[F, x]})#f] = Functor[({type f[x] = OptionT[F, x]})#f]
      def traverse[G[_], A, B](o: OptionT[F, A])(f: A => G[B])(implicit G: Applicative[G]): G[OptionT[F,B]] = o traverse f
    }

  implicit def optionTisMonoid[F[_]: Applicative, A: Monoid]: Monoid[OptionT[F, A]] =
    new Monoid[OptionT[F, A]] {
      val unit: OptionT[F, A] = OptionT.someT(Monoid[A].unit)
      def combine(l: OptionT[F, A], r: OptionT[F, A]): OptionT[F, A] = l.map2(r)(Monoid[A].combine)
    }
}
