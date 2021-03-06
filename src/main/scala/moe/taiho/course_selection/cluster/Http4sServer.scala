package moe.taiho.course_selection.cluster

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives.{onComplete, _}
import akka.http.scaladsl.model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.pattern.ask

import scala.io.Source
import akka.http.scaladsl.Http.ServerBinding
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import moe.taiho.course_selection.actors.{Course, Student}

import scala.concurrent.Promise
import scala.util.Success
import org.http4s._
import org.http4s.circe._
import org.http4s.server._
import org.http4s.dsl._
import fs2.Task
import org.http4s.server.blaze.BlazeBuilder

object SidQueryParaMatcher extends QueryParamDecoderMatcher[String]("sid")
object CidQueryParaMatcher extends QueryParamDecoderMatcher[String]("cid")
object SizeQueryParaMatcher extends QueryParamDecoderMatcher[String]("size")

object Http4sServer {
	def run(studentRegion: ActorRef, courseRegion: ActorRef)(implicit system: ActorSystem): Server = {

		implicit val strategy = fs2.Strategy.fromCachedDaemonPool()
		val service = HttpService {
			case GET -> Root / "hello" =>
				Ok(s"<h1>Say hello to akka-http</h1>")
			case GET -> Root / "ping" =>
				Ok({
				val t0 = System.nanoTime()
				val ping = new Pinger(studentRegion, courseRegion)
				ping.ping()
				val t1 = System.nanoTime()
				s"<h1>ping all actor in ${(t1-t0)/1000000} ms</h1>"
				})
			case POST -> Root / "demo" =>
				Task.fromFuture({
					val p = Promise[Response]
					val f = p.future
					implicit val askTimeout: Timeout = 10.seconds // set timeout
					(studentRegion ? Student.Envelope(10086, Student.DebugPrint("Debug Message"))).mapTo[String] onComplete {
						case Success(result) => p success Response(Status.Ok)
						case _ => p success Response(Status.RequestTimeout)//complete(s"The server was not able " + "to produce a timely response to your request.\r\nPlease try again in a short while!")
					}
					f
				})
			case POST -> Root / "take" :? SidQueryParaMatcher(studentId) +& CidQueryParaMatcher(courseId) =>
				val p = Promise[Response]
				val studentID = studentId.toInt + 1
				val courseID = courseId.toInt
				implicit val askTimeout: Timeout = 10.seconds // set timeout
				(studentRegion ? Student.Envelope(studentID, Student.Quit(courseID))).mapTo[Student.Info] onComplete {
					case Success(res) =>
						res match {
							case Student.Success(_, course, _) => p success Response(Status.Ok)//complete(s"Successes drop course'$course'")
							case Student.Failure(_, course, _, reason) => p success Response(Status.Ok)//complete(s"Failed to drop course'$course' becasue of '$reason'")
							case _ => p success Response(Status.BadGateway)//complete(HttpResponse(502, entity = "Something Wrong"))
						}
					case _ => p success Response(Status.RequestTimeout)//complete(HttpResponse(504, entity = "The server was not able " + "to produce a timely response to your request.\r\nPlease try again in a short while!"))
				}
				Task.fromFuture(p.future)
			case POST -> Root / "quit" :? SidQueryParaMatcher(studentId) +& CidQueryParaMatcher(courseId) =>
				val p = Promise[Response]
				val studentID = studentId.toInt + 1
				val courseID = courseId.toInt
				implicit val askTimeout: Timeout = 10.seconds // set timeout
				(studentRegion ? Student.Envelope(studentID, Student.Quit(courseID))).mapTo[Student.Info] onComplete {
					case Success(res) =>
						res match {
							case Student.Success(_, course, _) => p success Response(Status.Ok)//complete(s"Successes drop course'$course'")
							case Student.Failure(_, course, _, reason) => p success Response(Status.Ok)//complete(s"Failed to drop course'$course' becasue of '$reason'")
							case _ => p success Response(Status.BadGateway)//complete(HttpResponse(502, entity = "Something Wrong"))
						}
					case _ => p success Response(Status.Ok)//complete(HttpResponse(504, entity = "The server was not able " + "to produce a timely response to your request.\r\nPlease try again in a short while!"))
				}
				Task.fromFuture(p.future)
                /*
			case POST -> Root / "setlimit" :? CidQueryParaMatcher(courseId) +& SizeQueryParaMatcher(size) =>
				val p = Promise[Response]
				val courseID = courseId.toInt
				val lim = size.toInt
				implicit val askTimeout: Timeout = 10.seconds // set timeout
				(courseRegion ? Course.Envelope(courseID, Course.SetLimit(lim))).mapTo[Done] onComplete {
					case _ : Success[Done]=> p success Response(Status.Ok)//complete (HttpEntity (ContentTypes.`text/html(UTF-8)`, "<h1>Set Successfully!</h1>") )
					case _ => p success Response(Status.BadGateway)//complete(HttpResponse(504, entity = "Timeout"))
				}
				Task.fromFuture(p.future)
			case POST -> Root / "table" :? SidQueryParaMatcher(studentId) =>
				val p = Promise[Response]
				val studentID = studentId.toInt + 1
				implicit val askTimeout: Timeout = 10.seconds // set timeout
				(studentRegion ? Student.Envelope(studentID, Student.Table())).mapTo[Student.Info] onComplete {
					case Success(res) =>
						res match {
							case Student.Response(_, _, content) => p success Response(Status.Ok)//complete(s"You have selected: '$content'")
							case _ => p success Response(Status.BadGateway)//complete(HttpResponse(502, entity = "Something Wrong"))
						}
					case _ => p success Response(Status.RequestTimeout)//complete(HttpResponse(504, entity = "The server was not able " + "to produce a timely response to your request.\r\nPlease try again in a short while!"))
				}
				Task.fromFuture(p.future)
                */
		}
		BlazeBuilder.bindHttp(ConfigFactory.load().getInt("course-selection.http-port"), "0.0.0.0")
				.mountService(service, "/").run
	}
}
