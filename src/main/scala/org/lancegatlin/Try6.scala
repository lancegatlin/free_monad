//package org.lancegatlin
//
//import scala.collection.mutable
//import scala.concurrent.{ExecutionContext, Future}
//import scala.language.higherKinds
//
//object Try6 {
//  case class Person(id: Int, name: String, address: String)
//
//  // If AST is inner type of API then it won't be possible to mix ASTs from
//  // multiple APIs
//
//  // Some ASTs
//  sealed trait AST[+A] {
//    def map[B](f: (A) => B): AST[B] =
//      Map(this, f)
//    def flatMap[B](f: (A) => AST[B]): AST[B] =
//      FlatMap(this,f)
//    def |+|[B](other: AST[B]) : AST[(A,B)] =
//      Applicative(this, other)
//
//    def run() : Future[A]
//    def withFilter(f: A => Boolean) = ???
//  }
//  case class Value[A](a: A) extends AST[A] {
//    override def map[B](f: (A) => B): AST[B] = Value(f(a))
//    override def flatMap[B](f: (A) => AST[B]): AST[B] = f(a)
//    override def run() = Future.successful(a)
//  }
//  case class Applicative[A,B](ast1: AST[A], ast2: AST[B]) extends AST[(A,B)] {
//    override def run(): Future[(A, B)] = {
//      val f1 = ast1.run()
//      val f2 = ast2.run()
//      for { a <- f1; b <- f2 } yield (a,b)
//    }
//  }
//  case class Map[A,B](ast: AST[A], f: A => B) extends AST[B] {
//    override def run(): Future[B] = ast.run().map(f)
//  }
//  case class FlatMap[A,B](ast : AST[A], f: A => AST[B]) extends AST[B] {
//    override def run(): Future[B] = ast.run().map(f).flatMap(_.run())
//  }
//  case class Suspend[A,C](call: C, eval: C => Future[A]) extends AST[A] {
//    override def run(): Future[A] = eval(call)
//  }
//  object AST {
//    def apply[A](value: A) : AST[A] = Value(value)
//  }
//
//  trait PersonDb {
//    def findById(id: Int) : Future[Option[Person]]
//    def update(id: Int, p:Person) : Future[Unit]
//    def insert(id: Int, p:Person) : Future[Unit]
//  }
//
//  object PersonDbStub {
//    sealed trait Call[A]
//    case class FindById(id: Int) extends Call[Option[Person]]
//    case class Update(id: Int, p: Person) extends Call[Unit]
//    case class Insert(id: Int, p: Person) extends Call[Unit]
//  }
//  trait PersonDbStub {
//    import PersonDbStub._
//
//    def findById(id: Int) : AST[Option[Person]] =
//      Suspend(FindById(id))
//    def update(id: Int, p: Person) : AST[Unit] =
//      Suspend(Update(id,p))
//    def insert(id: Int, p: Person) : AST[Unit] =
//      Suspend(Insert(id,p))
//  }
//
//  trait PersonService {
//    def incrementAge(id: Int) : AST[Unit] = {
//
//    }
//  }
//}
