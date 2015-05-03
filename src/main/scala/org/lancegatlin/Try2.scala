package org.lancegatlin

import scala.language.higherKinds

object Try2 {
  case class Person(id: Int, name: String, address: String)

  trait Api {
    type Call[A]
    sealed trait AST[A] {
      def map[B](f: A => B) : AST[B]
      def flatMap[B](f: A => AST[B]) : AST[B]
    }
    case class Value[A](a: A) extends AST[A] {
      override def map[B](f: (A) => B): AST[B] = Value(f(a))
      override def flatMap[B](f: (A) => AST[B]): AST[B] = f(a)
    }
    trait Lazy[A] { self: AST[A] =>
      override def map[B](f: (A) => B): AST[B] =
        Map(self, f)
      override def flatMap[B](f: (A) => AST[B]): AST[B] =
        FlatMap(self,f)
    }
    case class Map[A,B](ast: AST[A], f: A => B) extends AST[B] with Lazy[B]
    case class FlatMap[A,B](ast : AST[A], f: A => AST[B]) extends AST[B] with Lazy[B]
    case class Suspend[A](call: Call[A]) extends AST[A] with Lazy[A]
    object AST {
      def apply[A](value: A) : AST[A] = Value(value)
    }

    def eval[A](call: Call[A]) : A
    def run[A](ast: AST[A]) : A = {
      ast match {
        case Value(a) => a
        case Suspend(call) => eval(call)
        case Map(ast,f) => f(run(ast))
        case FlatMap(ast,f) => run(f(run(ast)))
      }
    }
  }

  class PersonDao extends Api {
    sealed trait Call[A]
    case class FindById(id: Int) extends Call[Option[Person]]
    case class Insert(id: Int) extends Call[Unit]
    case class Update(id: Int, p: Person) extends Call[Unit]

    def findById(id: Int) : AST[Option[Person]] =
      Suspend(FindById(id))
    def insert(id: Int, p: Person) : AST[Unit] =
      Suspend(Insert(id))
    def update(id: Int, p: Person) : AST[Unit] =
      Suspend(Update(id,p))

    override def eval[A](call: Call[A]): A = {
      call match {
        case FindById(id) =>
          id match {
            case 1 => Some(Person(1, "lance", "atlanta"))
            case _ => None
          }
        case _ => ???
      }
    }
  }

  trait AddressDao extends Api {
    sealed trait Call[A]
    case class VerifyAddress(address: String) extends Call[Boolean]

    def verifyAddress(address: String) : AST[Boolean] =
      Suspend(VerifyAddress(address))

    override def eval[A](call: Call[A]): A = {
      call match {
        case VerifyAddress(address) => true
        case _ => ???
      }
    }
  }

  trait PersonService {

    val personDao : PersonDao = ???
    val addressDao : AddressDao = ???
    def updateAddress(id: Int, newAddress: String) : PersonDao#AST[Option[Person]] = {
      for {
        optPerson <- personDao.findById(id)
        result <- optPerson match {
          case Some(person) =>
            val updatedPerson = person.copy(address = newAddress)
            for {
              _ <- personDao.update(id, updatedPerson)
            } yield Some(updatedPerson)
          case None => personDao.AST(None)
        }
      } yield result
    }

  }

}
