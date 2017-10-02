package com.goyeau.orchestra.page

import java.time.Instant
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.scalajs.js.timers.SetIntervalHandle
import scala.scalajs.js

import autowire._
import com.goyeau.orchestra._
import com.goyeau.orchestra.ARunStatus._
import com.goyeau.orchestra.parameter.Parameter.State
import com.goyeau.orchestra.parameter.{ParameterOperations, RunId}
import com.goyeau.orchestra.route.WebRouter.{AppPage, TaskLogsPage}
import io.circe._
import io.circe.java8.time._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.builder.Lifecycle.{ComponentDidMount, RenderScope}
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import shapeless.HList

object JobBoardPage {
  case class Props[Params <: HList, ParamValues <: HList](
    name: String,
    job: Job.Definition[_, ParamValues, _],
    params: Params,
    ctrl: RouterCtl[AppPage]
  )(
    implicit val ec: ExecutionContext,
    paramOperations: ParameterOperations[Params, ParamValues],
    encoder: Encoder[ParamValues]
  ) {

    def runJob(state: (RunInfo, Map[Symbol, Any], Seq[TagMod], SetIntervalHandle))(event: ReactEventFromInput) =
      Callback.future {
        event.preventDefault()
        job.Api.client.trigger(state._1, paramOperations.values(params, state._2)).call().map {
          case ARunStatus.Failure(_, e) => Callback.alert(e.getMessage)
          case _                        => ctrl.set(TaskLogsPage(job, state._1.runId))
        }
      }

    def displays(
      $ : RenderScope[Props[_, _ <: HList], (RunInfo, Map[Symbol, Any], Seq[TagMod], SetIntervalHandle), Unit]
    ) = {
      val displayState = State(kv => $.modState(s => s.copy(_2 = s._2 + kv)), key => $.state._2.get(key))
      paramOperations.displays(params, displayState)
    }
  }

  val component =
    ScalaComponent
      .builder[Props[_, _ <: HList]](getClass.getSimpleName)
      .initialStateFromProps[(RunInfo, Map[Symbol, Any], Seq[TagMod], SetIntervalHandle)] { props =>
        val jobInfo = RunInfo(props.job.id, Option(UUID.randomUUID()))
        (jobInfo, Map(RunId.id -> jobInfo.runId), Seq(<.tr(<.td("Loading runs"))), null)
      }
      .renderP { ($, props) =>
        <.div(
          <.div(props.name),
          <.form(^.onSubmit ==> props.runJob($.state))(
            props.displays($) :+
              <.button(^.`type` := "submit")("Run"): _*
          ),
          <.div("History"),
          <.table(<.tbody($.state._3: _*))
        )
      }
      .componentDidMount { $ =>
        $.setState($.state.copy(_4 = js.timers.setInterval(1.second)(pullRuns($).runNow())))
          .flatMap(_ => pullRuns($))
      }
      .componentWillUnmount($ => Callback(js.timers.clearInterval($.state._4)))
      .build

  def pullRuns(
    $ : ComponentDidMount[Props[_, _ <: HList], (RunInfo, Map[Symbol, Any], Seq[TagMod], SetIntervalHandle), Unit]
  ) = {
    implicit val ec = $.props.ec
    Callback.future(
      $.props.job.Api.client
        .runs(Page(None, 50)) // TODO load more as we scroll
        .call()
        .map { runs =>
          val runDisplays = runs.map {
            case (uuid, createdAt, runStatus) =>
              val statusDisplay = runStatus match {
                case _: Triggered    => "Triggered"
                case _: Running      => "Running"
                case _: Success      => "Success"
                case _: Failure      => "Failure"
                case _: Stopped.type => "Stopped"
              }

              <.tr(
                <.td(<.button(^.onClick --> $.props.ctrl.set(TaskLogsPage($.props.job, uuid)))(uuid.toString)),
                <.td(createdAt.toString),
                <.td(statusDisplay)
              )
          }
          $.modState(_.copy(_3 = if (runDisplays.nonEmpty) runDisplays else Seq(<.tr(<.td("No job ran yet")))))
        }
    )
  }
}