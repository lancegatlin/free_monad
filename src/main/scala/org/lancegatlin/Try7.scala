package org.lancegatlin

import org.lancegatlin.Try7.ApiAst.ApiCallExecutor

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object Try7 {
  case class Person(name: String, age: Int)

  // Inspired by: http://underscore.io/blog/posts/2015/04/14/free-monads-are-simple.html

  // API - a trait with methods that return a Future of some value
  trait PersonDb { // example API
    def findById(id: Int) : Future[Option[Person]]
    def update(id: Int, p:Person) : Future[Unit]
    def insert(id: Int, p:Person) : Future[Unit]
  }

  // Free monad/AST - abstract syntax tree. A representation of a monadic/applicative
  // workflow that calls the underlying API. An AST can be executed or 
  // serialized for execution later or remotely. Because ASTs have a beginning
  // and end, they serve as an implicit "session". Session semantics could be
  // tied to caching, resource management or transaction scope.

  /* ApiCall is a closure and functor whose inner type is the return type of
   the closure. When given an instance of the API the ApiCall can connect
   its saved parameters to the actual API call.

   Example for API above:
    sealed trait ApiCall[A] extends (PersonDb => Future[A])
    case class FindById(id: Int) extends ApiCall[Option[Person]] {
      def apply(api: PersonDb) = api.findById(id)
    }
    case class Update(id: Int, p: Person) extends ApiCall[Unit] {
      def apply(api: PersonDb) = api.update(id,p)
    }
    case class Insert(id: Int, p: Person) extends ApiCall[Unit] {
      def apply(api: PersonDb) = api.insert(id,p)
    }
  */
  /* A is the ultimate return type of the AST */
  sealed trait ApiAst[ApiCall[_],+A] { // free monad that represents an AST that calls some API
    // Map and FlatMap are simply captured as case classes that lazily represent
    // the operation. They will be evaluated when the ast is executed
    def map[B](f: A => B): ApiAst[ApiCall,B] =
      ApiAst.Map(this, f)

    def flatMap[B](f: (A) => ApiAst[ApiCall,B]): ApiAst[ApiCall,B] =
      ApiAst.FlatMap(this,f)

    // Applicative allows creating ast branches that are executed in parallel
    def |@|[B](other: ApiAst[ApiCall,B]) : ApiAst[ApiCall,(A,B)] =
      ApiAst.Applicative(this, other)

    // Note: scalac complains if this isn't here, but haven't figured out what
    // it means to filter an AST result since AST is like Id monad
    def withFilter(f: A => Boolean) = ???
  }
  object ApiAst {
    def apply[ApiCall[_],A](value: A) : ApiAst[ApiCall,A] = Value[ApiCall,A](value)

    // Literals are handled directly to reduce the AST size
    case class Value[ApiCall[_],A](a: A) extends ApiAst[ApiCall,A] {
      override def map[B](f: A => B): ApiAst[ApiCall,B] =
        Value(f(a))
      override def flatMap[B](f: A => ApiAst[ApiCall, B]): ApiAst[ApiCall, B] =
        f(a)
    }

    case class Applicative[ApiCall[_],A,B](
      ast1: ApiAst[ApiCall,A],
      ast2: ApiAst[ApiCall,B]
    ) extends ApiAst[ApiCall,(A,B)]

    case class Map[ApiCall[_],A,B](
      ast: ApiAst[ApiCall,A],
      f: A => B
    ) extends ApiAst[ApiCall,B] {
      // Use andThen to combine map function to reduce size of AST
      override def map[C](f2: B => C): ApiAst[ApiCall, C] =
        Map(ast, f andThen f2)
      override def flatMap[C](f2: B => ApiAst[ApiCall, C]): ApiAst[ApiCall, C] =
        FlatMap(ast, f andThen f2)
    }

    case class FlatMap[ApiCall[_],A,B](
      ast : ApiAst[ApiCall,A],
      f: A => ApiAst[ApiCall,B]
    ) extends ApiAst[ApiCall,B]

    case class Suspend[ApiCall[_],A](call: ApiCall[A]) extends ApiAst[ApiCall,A]

    // Execute a call to the API
    trait ApiCallExecutor[ApiCall[_]] {
      def apply[A](call: ApiCall[A]) : Future[A]
    }

    // Default way to execute an AST. Note: this is just one way to execute an
    // AST, other methods could easily provide alternatives.
    def run[ApiCall[_],A](
      ast: ApiAst[ApiCall,A]
    )(implicit
      executor: ApiCallExecutor[ApiCall],
      ec: ExecutionContext
    ) : Future[A] = {
      def loop[B] : ApiAst[ApiCall,B] => Future[B] = {
        case Value(a) => Future.successful(a)
        case Applicative(ast1,ast2) =>
          // Run in parallel
          val f1 = loop(ast1)
          val f2 = loop(ast2)
          for {
            r1 <- f1
            r2 <- f2
          } yield (r1,r2)
        case Map(ast1, f) => loop(ast1).map(f)
        case FlatMap(ast1, f) => loop(ast1).flatMap(b => loop(f(b)))
        case Suspend(call) => executor(call)
      }
      loop(ast)
    }
  }

  // Simple implementation of example API
  class PersonDbImpl extends PersonDb {
    val data = mutable.Map[Int,Person]()
    override def findById(id: Int): Future[Option[Person]] =
      Future.successful(data.get(id))
    override def update(id: Int, p: Person): Future[Unit] =
      Future.successful(data.put(id,p))
    override def insert(id: Int, p: Person): Future[Unit] =
      Future.successful(data.put(id,p))
  }

  // Convenience trait for reducing stub declaration cruft
  trait ApiStub[API] {
    // Require ApiCall instances to bind a way to execute themselves with an
    // instance of the API
    protected trait ApiCall[A] {
      def execute : API => Future[A]
    }

    // Make Ast monad a bit more terse and nicer to work with
    type Ast[A] = ApiAst[ApiCall,A]
    object Ast {
      def apply[A](value: A) : Ast[A] = ApiAst.Value(value)
    }

    // Wrap ApiCall in Suspend implicitly
    import scala.language.implicitConversions
    protected implicit def apiCallToSuspend[A](call: ApiCall[A]) : Ast[A] =
      ApiAst.Suspend[ApiCall,A](call)

    protected def mkExecutor(api: API)  = new ApiAst.ApiCallExecutor[ApiCall] {
      override def apply[A](call: ApiCall[A]): Future[A] = call.execute(api)
    }

    trait Interpreter {
      implicit def executor: ApiAst.ApiCallExecutor[ApiCall]
      def apply[A](ast: Ast[A])(implicit ec:ExecutionContext) : Future[A] = {
        ApiAst.run(ast)
      }
    }
    // Make an interpreter for ASTs that calls the API instance
    def bind(api: API) = new Interpreter {
      override implicit val executor = new ApiCallExecutor[ApiCall] {
        override def apply[A](call: ApiCall[A]): Future[A] = call.execute(api)
      }
    }
  }

  // Global stub for interacting with PersonDb. Instead of making actual calls
  // returns an AST that can executed later with an instance of PersonDb
  object PersonDbStub extends ApiStub[PersonDb] {
    sealed class ApiCall[A](val execute: PersonDb => Future[A]) extends super.ApiCall[A]
    case class FindById(id: Int) extends ApiCall[Option[Person]](_.findById(id))
    case class Update(id: Int, p: Person) extends ApiCall[Unit](_.update(id,p))
    case class Insert(id: Int, p: Person) extends ApiCall[Unit](_.insert(id,p))

    def findById(id: Int) : Ast[Option[Person]] = FindById(id)
    def update(id: Int, p: Person) : Ast[Unit] = Update(id,p)
    def insert(id: Int, p: Person) : Ast[Unit] = Insert(id,p)
  }

  // Example service that can just be a global singleton since it requires no
  // DI and only returns ASTs
  object PersonService {
    import PersonDbStub.Ast
    val db = PersonDbStub
    def incrementAge(id: Int) : Ast[Option[Int]] = {
      for {
        optPerson <- db.findById(id)
        result <- {
          optPerson match {
            case Some(person) =>
              val updatedPerson = person.copy(age = person.age + 1)
              db.update(id, updatedPerson).map(_ => Some(updatedPerson.age))
            case None => Ast(None)
          }
        }
      } yield result
    }
  }

  // TODO: how to mix ASTs from more than one API?
  // TODO: how to execute an AST that depends on more than one API?
}

// Example (paste-able in console)
object Try7_Console_Pasteable {
  import org.lancegatlin.Try7._
  import scala.concurrent._
  import duration._
  import ExecutionContext.Implicits._
  val stub = PersonDbStub
  // Make an ast to create an entry
  val insertAst = stub.insert(1, Person("lance",37))
  // PersonService gives me an AST that is specific to PersonDb API
  val incAst = PersonService.incrementAge(1)
  // Note: nothing has actually happened yet
  val db = new PersonDbImpl
  // Bind an instance of PersonDb to create an interpreter that can execute the ASTs
  val interpreter = stub.bind(db)
  val result : Future[Option[Int]] = for {
    _ <- interpreter(insertAst)
    pass <- interpreter(incAst)
  } yield pass
  // ---
  // Warning: paste everything above this first, otherwise deadlock for unknown
  // reasons
  // ---
  Await.result(result,Duration.Inf)
}