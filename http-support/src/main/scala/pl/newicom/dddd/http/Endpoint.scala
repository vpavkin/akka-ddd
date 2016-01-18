package pl.newicom.dddd.http

import akka.http.scaladsl.server.{Directives, Route}

trait Endpoint[A] extends (A => Route) with Directives { outer =>
  def apply(a: A) = route(a)
  def route(a: A): Route

  def ~(other: Endpoint[A]): Endpoint[A] = Endpoint[A](a => outer.route(a) ~ other.route(a))
}

object Endpoint {
  def apply[A](f: A => Route): Endpoint[A] = new Endpoint[A] {
    override def route(a: A): Route = f(a)
  }
}

