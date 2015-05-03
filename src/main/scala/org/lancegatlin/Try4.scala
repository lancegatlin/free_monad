//package org.lancegatlin
//
//import scala.language.higherKinds
//
//object Try4 {
//  case class Person(id: Int, name: String, address: String)
//
//  trait Api {
//    type Call[A]
//    type AST[A] = _AST[Call[A],A]
//    implicit def eval[A] : Call[A] => A
//
//    def run[A](ast: AST[A]) : A = ???
////    {
////      ast match {
////        case Value(a) => a
////        case Map(ast,f) => f(run(ast))
////        case FlatMap(ast,f) => run(f(run(ast)))
////        case s@Suspend(call) => eval(call)
////      }
////    }
//  }
//
//  sealed trait _AST[+Call[_],+A] {
//    def map[B](f: (A) => B): _AST[Call,B] =
//      Map(this, f)
//    def flatMap[B](f: (A) => _AST[Call,B]): _AST[Call,B] =
//      FlatMap(this,f)
//  }
//  case class Value[A](a: A) extends _AST[Nothing,A] {
//    override def map[B](f: (A) => B): _AST[Nothing, B] = Value(f(a))
//    override def flatMap[B,Call[_]](f: (A) => _AST[Call,B]): _AST[Call,B] = f(a)
//  }
//  case class Map[Call[_],A,B](ast: _AST[Call,A], f: A => B) extends _AST[Call,B]
//  case class FlatMap[Call[_],A,B](ast : _AST[Call,A], f: A => _AST[Call,B]) extends _AST[Call,B]
//  case class Suspend[Call[_],A](c: Call[A]) extends _AST[Call,A]
//  object _AST {
//    def apply[A](value: A) : _AST[Nothing,A] = Value(value)
//  }
//
//  class PersonDao extends Api {
//    sealed trait Call[A]
//    case class FindById(id: Int) extends Call[Option[Person]]
//    case class Insert(id: Int) extends Call[Unit]
//    case class Update(id: Int, p: Person) extends Call[Unit]
//
//    implicit def run[A] : Call[A] => A = {
//      case FindById(id) =>
//        id match {
//          case 1 => Some(Person(1, "lance", "atlanta"))
//          case _ => None
//        }
//    }
//    def findById(id: Int) : AST[Option[Person]] =
//      Suspend[Call,Option[Person]](FindById(id))
//    def insert(id: Int, p: Person) : AST[Unit] =
//      Suspend(Insert(id))
//    def update(id: Int, p: Person) : AST[Unit] =
//      Suspend(Update(id,p))
//
//  }
//
//  class AddressDao extends Api {
//    sealed trait Call[A]
//    case class VerifyAddress(address: String) extends Call[Boolean]
//
//
//    override implicit def run[A]: (Call[A]) => A = {
//      case VerifyAddress(address) => true
//    }
//
//    def verifyAddress(address: String) : AST[Boolean] =
//      Suspend(VerifyAddress(address))
//
//  }
//
//  trait PersonService {
//
//    val personDao : PersonDao = ???
//    val addressDao : AddressDao = ???
//    def updateAddress(id: Int, newAddress: String) : AST[Option[Person]] = {
//      for {
//        optPerson <- personDao.findById(id)
//        result <- optPerson match {
//          case Some(person) =>
//            for {
//              valid <- addressDao.verifyAddress(newAddress)
//              optUpdatedPerson <- {
//                if(valid) {
//                  val updatedPerson = person.copy(address = newAddress)
//                  personDao.update(id,updatedPerson).map(_ => Some(updatedPerson))
//                } else {
//                  AST(None)
//                }
//              }
//            } yield optUpdatedPerson
//          case None => AST(None)
//        }
//      } yield result
//    }
//
//  }
//
//}
