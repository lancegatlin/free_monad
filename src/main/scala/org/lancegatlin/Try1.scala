package org.lancegatlin

object Try1 {
  case class Person(id: Int, name: String, age: Int)
  trait Api {
    sealed trait AST[A] {
      def run : A = Api.this.run(this)
      def map[B](f: A => B) : AST[B]
      def flatMap[B](f: A => Api#AST[B]) : AST[B]
    }
    object AST {
      case class Value[A](value: A) extends AST[A] {
        override def map[B](f: (A) => B): AST[B] =
          Value(f(value))
        override def flatMap[B](f: (A) => Api#AST[B]): AST[B] =
          Suspend(f(value))
      }
      case class Suspend[A](ast: Api#AST[A]) extends AST[A] {
        override def map[B](f: A => B): AST[B] =
          Suspend(ast.map(f))
        override def flatMap[B](f: (A) => Api#AST[B]): AST[B] =
          Suspend(ast.flatMap(f))
      }
      def apply[A](value: A) : AST[A] = Value(value)
    }
    def run[A](io: AST[A]) : A
  }

  trait PersonDao extends Api {
    def findById(id: Int) : AST[Option[Person]]
    def insert(id: Int, p: Person) : AST[Unit]
    def update(id: Int, p: Person) : AST[Unit]

    override def run[A](io: AST[A]): A = {
      io match {
        case AST.Value(a) => a
        case AST.Suspend(ast) => ast.run
      }
    }
  }

  trait PersonService extends Api {
    val dao : PersonDao = ???
    def incrementAge(id: Int) : PersonDao#AST[Option[Person]] =
      for {
        optPerson <- dao.findById(id)
        result <- optPerson match {
          case Some(person) =>
            val updatedPerson = person.copy(age = person.age + 1)
            for {
              _ <- dao.update(id, updatedPerson)
            } yield Some(updatedPerson)
          case None =>
            dao.AST(None)
        }
      } yield result

  }

}